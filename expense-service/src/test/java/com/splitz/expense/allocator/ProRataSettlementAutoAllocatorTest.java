package com.splitz.expense.allocator;

import static org.assertj.core.api.Assertions.assertThat;

import com.splitz.expense.model.SettlementAllocation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The settlement-allocation decision is a pure distribution function over a {@link DebtPosition}:
 * no BalanceService dependency, no wiring. The allocator is the second consumer of the resolved
 * debt picture, so every expectation here is expressed as DebtPosition → allocations (the interface
 * is the test surface) — the same table the previous BalanceService-mocked test wanted, minus
 * setup.
 */
class ProRataSettlementAutoAllocatorTest {

  private final ProRataSettlementAutoAllocator allocator = new ProRataSettlementAutoAllocator();

  private static DebtPosition position(GroupDebt... debts) {
    return DebtPosition.builder().payerId(1L).payeeId(2L).debts(List.of(debts)).build();
  }

  private static GroupDebt debt(Long groupId, String owedAmount) {
    return GroupDebt.builder().groupId(groupId).owedAmount(new BigDecimal(owedAmount)).build();
  }

  @Test
  @DisplayName("Should allocate entire payment to single group when owed amount matches")
  void shouldAllocateEntirePaymentToSingleGroupWhenOwedAmountMatches() {
    List<SettlementAllocation> allocations =
        allocator.allocate(position(debt(10L, "50.00")), new BigDecimal("50.00"));

    assertThat(allocations).hasSize(1);
    assertThat(allocations.get(0).getGroupId()).isEqualTo(10L);
    assertThat(allocations.get(0).getAmount()).isEqualByComparingTo("50.00");
  }

  @Test
  @DisplayName("Should allocate pro-rata across multiple groups according to debt proportions")
  void shouldAllocateProRataAcrossMultipleGroups() {
    // Group 10 owes $30, Group 20 owes $70. Total owed = $100.
    // Pro-rata shares: Group 10 -> 30%, Group 20 -> 70%.
    // Payment of $50 -> Group 10 gets $15.00, Group 20 gets $35.00.
    List<SettlementAllocation> allocations =
        allocator.allocate(
            position(debt(10L, "30.00"), debt(20L, "70.00")), new BigDecimal("50.00"));

    assertThat(allocations).hasSize(2);
    assertThat(allocations.get(0).getGroupId()).isEqualTo(10L);
    assertThat(allocations.get(0).getAmount()).isEqualByComparingTo("15.00");
    assertThat(allocations.get(1).getGroupId()).isEqualTo(20L);
    assertThat(allocations.get(1).getAmount()).isEqualByComparingTo("35.00");
  }

  @Test
  @DisplayName("Should handle penny rounding adjustment without losing or adding total amount")
  void shouldHandlePennyRoundingAdjustment() {
    // 3 groups with equal $10 debt each ($30 total). $10 payment.
    // Each group unrounded share: 3.3333... -> $3.33, $3.33, last gets $3.34
    List<SettlementAllocation> allocations =
        allocator.allocate(
            position(debt(1L, "10.00"), debt(2L, "10.00"), debt(3L, "10.00")),
            new BigDecimal("10.00"));

    assertThat(allocations).hasSize(3);
    BigDecimal sum =
        allocations.stream()
            .map(SettlementAllocation::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sum).isEqualByComparingTo("10.00");
  }

  @Test
  @DisplayName(
      "Should allocate excess amount to global unallocated (null groupId) when payment exceeds debt")
  void shouldAllocateExcessToGlobalWhenPaymentExceedsOwedDebt() {
    // Group 10 has $30 debt. Total debt = $30. Payment = $100.
    // $30 goes to Group 10, remaining $70 goes to null groupId.
    List<SettlementAllocation> allocations =
        allocator.allocate(position(debt(10L, "30.00")), new BigDecimal("100.00"));

    assertThat(allocations).hasSize(2);
    assertThat(allocations.get(0).getGroupId()).isEqualTo(10L);
    assertThat(allocations.get(0).getAmount()).isEqualByComparingTo("30.00");
    assertThat(allocations.get(1).getGroupId()).isNull();
    assertThat(allocations.get(1).getAmount()).isEqualByComparingTo("70.00");
  }

  @Test
  @DisplayName("Should allocate entirely to global when payer owes no debt across groups")
  void shouldAllocateEntirelyToGlobalWhenPayerOwesNothing() {
    List<SettlementAllocation> allocations =
        allocator.allocate(position(), new BigDecimal("50.00"));

    assertThat(allocations).hasSize(1);
    assertThat(allocations.get(0).getGroupId()).isNull();
    assertThat(allocations.get(0).getAmount()).isEqualByComparingTo("50.00");
  }

  @Test
  @DisplayName("Should allocate entirely to global for a null position or null debt list")
  void shouldAllocateToGlobalForNullPositionOrNullDebts() {
    assertThat(allocator.allocate(null, new BigDecimal("50.00")))
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.getGroupId()).isNull();
              assertThat(a.getAmount()).isEqualByComparingTo("50.00");
            });

    DebtPosition nullDebts = DebtPosition.builder().payerId(1L).payeeId(2L).debts(null).build();
    assertThat(allocator.allocate(nullDebts, new BigDecimal("50.00")))
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.getGroupId()).isNull();
              assertThat(a.getAmount()).isEqualByComparingTo("50.00");
            });
  }

  @Test
  @DisplayName("Should return no allocations for a null or non-positive payment amount")
  void shouldReturnNoAllocationsForNonPositiveAmount() {
    assertThat(allocator.allocate(position(debt(10L, "50.00")), null)).isEmpty();
    assertThat(allocator.allocate(position(debt(10L, "50.00")), BigDecimal.ZERO)).isEmpty();
    assertThat(allocator.allocate(position(debt(10L, "50.00")), new BigDecimal("-5.00"))).isEmpty();
  }
}
