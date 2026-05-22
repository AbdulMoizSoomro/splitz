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
import org.junit.jupiter.api.Test;

class FifoSettlementAutoAllocatorTest {

  private BalanceService balanceService;
  private FifoSettlementAutoAllocator allocator;

  @BeforeEach
  void setUp() {
    balanceService = mock(BalanceService.class);
    allocator = new FifoSettlementAutoAllocator(balanceService);
  }

  @Test
  void shouldAllocateEntirePaymentToSingleGroupWhenOwedAmountMatchesExactly() {
    Long payerId = 1L;
    Long payeeId = 2L;
    BigDecimal paymentAmount = new BigDecimal("50.00");

    FriendBalanceResponseDTO mockBalanceResponse =
        FriendBalanceResponseDTO.builder()
            .userId(payerId)
            .friendId(payeeId)
            .netBalance(new BigDecimal("-50.00"))
            .groupBalances(
                List.of(
                    FriendGroupBalanceDTO.builder()
                        .groupId(10L)
                        .groupName("Test Group")
                        .balance(new BigDecimal("-50.00")) // negative means payer owes payee
                        .build()))
            .build();

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(mockBalanceResponse);

    List<SettlementAllocation> allocations = allocator.allocate(payerId, payeeId, paymentAmount);

    assertThat(allocations).hasSize(1);
    SettlementAllocation allocation = allocations.get(0);
    assertThat(allocation.getGroupId()).isEqualTo(10L);
    assertThat(allocation.getAmount()).isEqualByComparingTo("50.00");
  }

  @Test
  void shouldAllocateAcrossMultipleGroupsInFifoOldestFirstOrder() {
    Long payerId = 1L;
    Long payeeId = 2L;
    BigDecimal paymentAmount = new BigDecimal("50.00");

    // Setup: Group 10 created first (oldest, ID 10) owes $30, Group 20 created later owes $40.
    // Total owed: $70. We pay $50.
    FriendBalanceResponseDTO mockBalanceResponse =
        FriendBalanceResponseDTO.builder()
            .userId(payerId)
            .friendId(payeeId)
            .netBalance(new BigDecimal("-70.00"))
            .groupBalances(
                List.of(
                    FriendGroupBalanceDTO.builder()
                        .groupId(20L)
                        .groupName("Newer Group")
                        .balance(new BigDecimal("-40.00"))
                        .build(),
                    FriendGroupBalanceDTO.builder()
                        .groupId(10L)
                        .groupName("Oldest Group")
                        .balance(new BigDecimal("-30.00"))
                        .build()))
            .build();

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(mockBalanceResponse);

    List<SettlementAllocation> allocations = allocator.allocate(payerId, payeeId, paymentAmount);

    assertThat(allocations).hasSize(2);

    // Oldest group (ID 10) gets settled first, up to its total debt of $30.00
    SettlementAllocation firstAllocation = allocations.get(0);
    assertThat(firstAllocation.getGroupId()).isEqualTo(10L);
    assertThat(firstAllocation.getAmount()).isEqualByComparingTo("30.00");

    // Newer group (ID 20) gets the remainder of $20.00
    SettlementAllocation secondAllocation = allocations.get(1);
    assertThat(secondAllocation.getGroupId()).isEqualTo(20L);
    assertThat(secondAllocation.getAmount()).isEqualByComparingTo("20.00");
  }

  @Test
  void shouldAllocateRemainingAmountToGlobalWhenPaymentExceedsOwedDebt() {
    Long payerId = 1L;
    Long payeeId = 2L;
    BigDecimal paymentAmount = new BigDecimal("100.00");

    // Setup: Group 10 has debt $30. Total debt is $30. We pay $100.
    // Excess $70 should go to global (groupId = null).
    FriendBalanceResponseDTO mockBalanceResponse =
        FriendBalanceResponseDTO.builder()
            .userId(payerId)
            .friendId(payeeId)
            .netBalance(new BigDecimal("-30.00"))
            .groupBalances(
                List.of(
                    FriendGroupBalanceDTO.builder()
                        .groupId(10L)
                        .groupName("Group 1")
                        .balance(new BigDecimal("-30.00"))
                        .build()))
            .build();

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(mockBalanceResponse);

    List<SettlementAllocation> allocations = allocator.allocate(payerId, payeeId, paymentAmount);

    assertThat(allocations).hasSize(2);

    SettlementAllocation firstAllocation = allocations.get(0);
    assertThat(firstAllocation.getGroupId()).isEqualTo(10L);
    assertThat(firstAllocation.getAmount()).isEqualByComparingTo("30.00");

    SettlementAllocation secondAllocation = allocations.get(1);
    assertThat(secondAllocation.getGroupId()).isNull();
    assertThat(secondAllocation.getAmount()).isEqualByComparingTo("70.00");
  }

  @Test
  void shouldAllocateEntirelyToGlobalWhenPayerOwesNothing() {
    Long payerId = 1L;
    Long payeeId = 2L;
    BigDecimal paymentAmount = new BigDecimal("50.00");

    // Setup: Payer has no shared groups, net balance is 0 or positive.
    FriendBalanceResponseDTO mockBalanceResponse =
        FriendBalanceResponseDTO.builder()
            .userId(payerId)
            .friendId(payeeId)
            .netBalance(BigDecimal.ZERO)
            .groupBalances(List.of())
            .build();

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(mockBalanceResponse);

    List<SettlementAllocation> allocations = allocator.allocate(payerId, payeeId, paymentAmount);

    assertThat(allocations).hasSize(1);
    SettlementAllocation allocation = allocations.get(0);
    assertThat(allocation.getGroupId()).isNull();
    assertThat(allocation.getAmount()).isEqualByComparingTo("50.00");
  }
}
