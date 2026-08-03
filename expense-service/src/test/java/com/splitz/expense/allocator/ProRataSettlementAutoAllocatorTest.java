package com.splitz.expense.allocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.splitz.expense.dto.FriendBalanceResponseDTO;
import com.splitz.expense.dto.FriendGroupBalanceDTO;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.service.BalanceService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProRataSettlementAutoAllocatorTest {

  private BalanceService balanceService;
  private ProRataSettlementAutoAllocator allocator;

  @BeforeEach
  void setUp() {
    balanceService = mock(BalanceService.class);
    allocator = new ProRataSettlementAutoAllocator(balanceService);
  }

  @Test
  @DisplayName("Should allocate entire payment to single group when owed amount matches")
  void shouldAllocateEntirePaymentToSingleGroupWhenOwedAmountMatches() {
    Long payerId = 1L;
    Long payeeId = 2L;
    BigDecimal paymentAmount = new BigDecimal("50.00");

    FriendBalanceResponseDTO balanceResponse =
        FriendBalanceResponseDTO.builder()
            .userId(payerId)
            .friendId(payeeId)
            .netBalance(new BigDecimal("-50.00"))
            .groupBalances(
                List.of(
                    FriendGroupBalanceDTO.builder()
                        .groupId(10L)
                        .groupName("Group A")
                        .balance(new BigDecimal("-50.00"))
                        .build()))
            .build();

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(balanceResponse);

    List<SettlementAllocation> allocations = allocator.allocate(payerId, payeeId, paymentAmount);

    assertThat(allocations).hasSize(1);
    assertThat(allocations.get(0).getGroupId()).isEqualTo(10L);
    assertThat(allocations.get(0).getAmount()).isEqualByComparingTo("50.00");
  }

  @Test
  @DisplayName("Should allocate pro-rata across multiple groups according to debt proportions")
  void shouldAllocateProRataAcrossMultipleGroups() {
    Long payerId = 1L;
    Long payeeId = 2L;
    BigDecimal paymentAmount = new BigDecimal("50.00");

    // Group 10 owes $30, Group 20 owes $70. Total owed = $100.
    // Pro-rata shares: Group 10 -> 30%, Group 20 -> 70%.
    // Payment of $50 -> Group 10 gets $15.00, Group 20 gets $35.00.
    FriendBalanceResponseDTO balanceResponse =
        FriendBalanceResponseDTO.builder()
            .userId(payerId)
            .friendId(payeeId)
            .netBalance(new BigDecimal("-100.00"))
            .groupBalances(
                List.of(
                    FriendGroupBalanceDTO.builder()
                        .groupId(10L)
                        .groupName("Group 10")
                        .balance(new BigDecimal("-30.00"))
                        .build(),
                    FriendGroupBalanceDTO.builder()
                        .groupId(20L)
                        .groupName("Group 20")
                        .balance(new BigDecimal("-70.00"))
                        .build()))
            .build();

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(balanceResponse);

    List<SettlementAllocation> allocations = allocator.allocate(payerId, payeeId, paymentAmount);

    assertThat(allocations).hasSize(2);
    assertThat(allocations.get(0).getGroupId()).isEqualTo(10L);
    assertThat(allocations.get(0).getAmount()).isEqualByComparingTo("15.00");

    assertThat(allocations.get(1).getGroupId()).isEqualTo(20L);
    assertThat(allocations.get(1).getAmount()).isEqualByComparingTo("35.00");
  }

  @Test
  @DisplayName("Should handle penny rounding adjustment without losing or adding total amount")
  void shouldHandlePennyRoundingAdjustment() {
    Long payerId = 1L;
    Long payeeId = 2L;
    BigDecimal paymentAmount = new BigDecimal("10.00");

    // 3 groups with equal $10 debt each ($30 total). $10 payment.
    // Each group unrounded share: 3.3333... -> $3.33, $3.33, last gets $3.34
    FriendBalanceResponseDTO balanceResponse =
        FriendBalanceResponseDTO.builder()
            .userId(payerId)
            .friendId(payeeId)
            .netBalance(new BigDecimal("-30.00"))
            .groupBalances(
                List.of(
                    FriendGroupBalanceDTO.builder()
                        .groupId(1L)
                        .groupName("Group 1")
                        .balance(new BigDecimal("-10.00"))
                        .build(),
                    FriendGroupBalanceDTO.builder()
                        .groupId(2L)
                        .groupName("Group 2")
                        .balance(new BigDecimal("-10.00"))
                        .build(),
                    FriendGroupBalanceDTO.builder()
                        .groupId(3L)
                        .groupName("Group 3")
                        .balance(new BigDecimal("-10.00"))
                        .build()))
            .build();

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(balanceResponse);

    List<SettlementAllocation> allocations = allocator.allocate(payerId, payeeId, paymentAmount);

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
    Long payerId = 1L;
    Long payeeId = 2L;
    BigDecimal paymentAmount = new BigDecimal("100.00");

    // Group 10 has $30 debt. Total debt = $30. Payment = $100.
    // $30 goes to Group 10, remaining $70 goes to null groupId.
    FriendBalanceResponseDTO balanceResponse =
        FriendBalanceResponseDTO.builder()
            .userId(payerId)
            .friendId(payeeId)
            .netBalance(new BigDecimal("-30.00"))
            .groupBalances(
                List.of(
                    FriendGroupBalanceDTO.builder()
                        .groupId(10L)
                        .groupName("Group 10")
                        .balance(new BigDecimal("-30.00"))
                        .build()))
            .build();

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(balanceResponse);

    List<SettlementAllocation> allocations = allocator.allocate(payerId, payeeId, paymentAmount);

    assertThat(allocations).hasSize(2);
    assertThat(allocations.get(0).getGroupId()).isEqualTo(10L);
    assertThat(allocations.get(0).getAmount()).isEqualByComparingTo("30.00");

    assertThat(allocations.get(1).getGroupId()).isNull();
    assertThat(allocations.get(1).getAmount()).isEqualByComparingTo("70.00");
  }

  @Test
  @DisplayName("Should allocate entirely to global when payer owes no debt across groups")
  void shouldAllocateEntirelyToGlobalWhenPayerOwesNothing() {
    Long payerId = 1L;
    Long payeeId = 2L;
    BigDecimal paymentAmount = new BigDecimal("50.00");

    FriendBalanceResponseDTO balanceResponse =
        FriendBalanceResponseDTO.builder()
            .userId(payerId)
            .friendId(payeeId)
            .netBalance(BigDecimal.ZERO)
            .groupBalances(List.of())
            .build();

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(balanceResponse);

    List<SettlementAllocation> allocations = allocator.allocate(payerId, payeeId, paymentAmount);

    assertThat(allocations).hasSize(1);
    assertThat(allocations.get(0).getGroupId()).isNull();
    assertThat(allocations.get(0).getAmount()).isEqualByComparingTo("50.00");
  }
}
