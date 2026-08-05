package com.splitz.expense.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.model.SplitType;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.PaymentRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultDebtBalanceEngineTest {

  private ExpenseRepository expenseRepository;
  private SettlementAllocationRepository settlementAllocationRepository;
  private PaymentRepository paymentRepository;
  private DefaultDebtBalanceEngine engine;

  @BeforeEach
  void setUp() {
    expenseRepository = mock(ExpenseRepository.class);
    settlementAllocationRepository = mock(SettlementAllocationRepository.class);
    paymentRepository = mock(PaymentRepository.class);
    engine =
        new DefaultDebtBalanceEngine(
            expenseRepository, settlementAllocationRepository, paymentRepository);
  }

  @Test
  void shouldCalculateBalancesForGroupWithOnlyExpensesAndEqualSplits() {
    // Setup: 3 members: 1, 2, 3.
    // Expense 1: User 1 pays 30.00, split equally between 1, 2, and 3.
    List<Long> memberIds = List.of(1L, 2L, 3L);

    Expense expense =
        Expense.builder()
            .paidBy(1L)
            .amount(new BigDecimal("30.00"))
            .splits(new ArrayList<>())
            .build();

    expense
        .getSplits()
        .add(
            ExpenseSplit.builder()
                .expense(expense)
                .userId(1L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("10.00"))
                .build());
    expense
        .getSplits()
        .add(
            ExpenseSplit.builder()
                .expense(expense)
                .userId(2L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("10.00"))
                .build());
    expense
        .getSplits()
        .add(
            ExpenseSplit.builder()
                .expense(expense)
                .userId(3L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("10.00"))
                .build());

    Map<Long, BigDecimal> balances =
        engine.calculateGroupBalances(memberIds, List.of(expense), List.of());

    // Expected:
    // User 1 paid 30, share 10 -> +20.00
    // User 2 paid 0, share 10 -> -10.00
    // User 3 paid 0, share 10 -> -10.00
    assertThat(balances).hasSize(3);
    assertThat(balances.get(1L)).isEqualByComparingTo("20.00");
    assertThat(balances.get(2L)).isEqualByComparingTo("-10.00");
    assertThat(balances.get(3L)).isEqualByComparingTo("-10.00");
  }

  @Test
  void shouldCalculateBalancesForGroupWithExpensesAndSettlements() {
    List<Long> memberIds = List.of(1L, 2L, 3L);

    // Expense 1: User 1 paid 30.00, split equally between 1, 2, 3
    Expense expense =
        Expense.builder()
            .paidBy(1L)
            .amount(new BigDecimal("30.00"))
            .splits(new ArrayList<>())
            .build();

    expense
        .getSplits()
        .add(
            ExpenseSplit.builder()
                .expense(expense)
                .userId(1L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("10.00"))
                .build());
    expense
        .getSplits()
        .add(
            ExpenseSplit.builder()
                .expense(expense)
                .userId(2L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("10.00"))
                .build());
    expense
        .getSplits()
        .add(
            ExpenseSplit.builder()
                .expense(expense)
                .userId(3L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("10.00"))
                .build());

    // Settlement: User 2 pays User 1 10.00
    com.splitz.expense.model.Payment payment =
        com.splitz.expense.model.Payment.builder()
            .payerId(2L)
            .payeeId(1L)
            .amount(new BigDecimal("10.00"))
            .status(com.splitz.expense.model.SettlementStatus.COMPLETED)
            .build();

    SettlementAllocation allocation =
        SettlementAllocation.builder()
            .groupId(10L)
            .amount(new BigDecimal("10.00"))
            .payment(payment)
            .build();

    Map<Long, BigDecimal> balances =
        engine.calculateGroupBalances(memberIds, List.of(expense), List.of(allocation));

    // Expected:
    // User 1 paid 30, share 10, received 10 settlement -> +10.00
    // User 2 paid 0, share 10, paid 10 settlement -> 0.00
    // User 3 paid 0, share 10, no settlement -> -10.00
    assertThat(balances.get(1L)).isEqualByComparingTo("10.00");
    assertThat(balances.get(2L)).isEqualByComparingTo("0.00");
    assertThat(balances.get(3L)).isEqualByComparingTo("-10.00");
  }

  @Test
  void shouldCalculateNetBalanceBetweenTwoUsersInGroup() {
    // Setup:
    // User 1 paid 30.00, split: 10.00 for User 1, 10.00 for User 2, 10.00 for User 3
    Expense expense =
        Expense.builder()
            .paidBy(1L)
            .amount(new BigDecimal("30.00"))
            .splits(new ArrayList<>())
            .build();
    expense
        .getSplits()
        .add(
            ExpenseSplit.builder()
                .expense(expense)
                .userId(1L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("10.00"))
                .build());
    expense
        .getSplits()
        .add(
            ExpenseSplit.builder()
                .expense(expense)
                .userId(2L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("10.00"))
                .build());
    expense
        .getSplits()
        .add(
            ExpenseSplit.builder()
                .expense(expense)
                .userId(3L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("10.00"))
                .build());

    // Settlement: User 2 paid User 1 4.00
    com.splitz.expense.model.Payment payment =
        com.splitz.expense.model.Payment.builder()
            .payerId(2L)
            .payeeId(1L)
            .amount(new BigDecimal("4.00"))
            .status(com.splitz.expense.model.SettlementStatus.COMPLETED)
            .build();
    SettlementAllocation allocation =
        SettlementAllocation.builder()
            .groupId(10L)
            .amount(new BigDecimal("4.00"))
            .payment(payment)
            .build();

    // From perspective of User 1 towards User 2:
    // User 1 paid split for User 2: +10.00 (friend owes user)
    // User 2 paid split for User 1: 0.00
    // User 2 settled to User 1: -4.00 (user received settlement, reducing friend's debt to user)
    // Net: +6.00
    BigDecimal netBalance12 =
        engine.calculateNetBalanceInGroup(1L, 2L, List.of(expense), List.of(allocation));
    assertThat(netBalance12).isEqualByComparingTo("6.00");

    // From perspective of User 2 towards User 1:
    // User 2 paid split for User 1: 0.00
    // User 1 paid split for User 2: -10.00 (user owes friend)
    // User 2 settled to User 1: +4.00 (user paid settlement, reducing user's debt to friend)
    // Net: -6.00
    BigDecimal netBalance21 =
        engine.calculateNetBalanceInGroup(2L, 1L, List.of(expense), List.of(allocation));
    assertThat(netBalance21).isEqualByComparingTo("-6.00");
  }

  @Test
  void shouldCalculateUserBalanceInGroupFromAggregates() {
    Long userId = 1L;
    Long groupId = 10L;

    when(expenseRepository.calculateTotalPaidByUserInGroup(userId, groupId))
        .thenReturn(new BigDecimal("50.00"));
    when(expenseRepository.calculateTotalShareForUserInGroup(userId, groupId))
        .thenReturn(new BigDecimal("20.00"));
    when(settlementAllocationRepository.calculateTotalSettlementsPaidByUserInGroup(
            userId, groupId, SettlementStatus.COMPLETED))
        .thenReturn(new BigDecimal("10.00"));
    when(settlementAllocationRepository.calculateTotalSettlementsPaidByUserInGroup(
            userId, groupId, SettlementStatus.MARKED_PAID))
        .thenReturn(BigDecimal.ZERO);
    when(settlementAllocationRepository.calculateTotalSettlementsReceivedByUserInGroup(
            userId, groupId, SettlementStatus.COMPLETED))
        .thenReturn(new BigDecimal("5.00"));
    when(settlementAllocationRepository.calculateTotalSettlementsReceivedByUserInGroup(
            userId, groupId, SettlementStatus.MARKED_PAID))
        .thenReturn(BigDecimal.ZERO);

    // Paid (50) - Share (20) + SettledPaid (10) - SettledReceived (5) = +35.00
    BigDecimal balance = engine.calculateUserBalanceInGroup(userId, groupId);
    assertThat(balance).isEqualByComparingTo("35.00");
  }

  @Test
  void shouldCalculateGlobalSettlementBalanceBetweenUsers() {
    Long userId = 1L;
    Long friendId = 2L;

    when(settlementAllocationRepository.calculateTotalSettledBetweenUsersInGroup(
            userId, friendId, null, SettlementStatus.COMPLETED))
        .thenReturn(new BigDecimal("15.00"));
    when(settlementAllocationRepository.calculateTotalSettledBetweenUsersInGroup(
            userId, friendId, null, SettlementStatus.MARKED_PAID))
        .thenReturn(BigDecimal.ZERO);
    when(settlementAllocationRepository.calculateTotalSettledBetweenUsersInGroup(
            friendId, userId, null, SettlementStatus.COMPLETED))
        .thenReturn(new BigDecimal("5.00"));
    when(settlementAllocationRepository.calculateTotalSettledBetweenUsersInGroup(
            friendId, userId, null, SettlementStatus.MARKED_PAID))
        .thenReturn(BigDecimal.ZERO);

    // User paid 15 to friend, friend paid 5 to user => Net +10.00
    BigDecimal balance = engine.calculateGlobalSettlementBalance(userId, friendId);
    assertThat(balance).isEqualByComparingTo("10.00");
  }

  @Test
  void shouldCalculateUserGlobalSettlementBalance() {
    Long userId = 1L;

    com.splitz.expense.model.Payment payment =
        com.splitz.expense.model.Payment.builder()
            .payerId(userId)
            .payeeId(2L)
            .amount(new BigDecimal("25.00"))
            .status(SettlementStatus.COMPLETED)
            .allocations(new ArrayList<>())
            .build();
    payment
        .getAllocations()
        .add(
            SettlementAllocation.builder()
                .groupId(null)
                .amount(new BigDecimal("25.00"))
                .payment(payment)
                .build());

    when(paymentRepository.findByPayerIdOrPayeeId(userId, userId)).thenReturn(List.of(payment));

    BigDecimal balance = engine.calculateUserGlobalSettlementBalance(userId);
    assertThat(balance).isEqualByComparingTo("25.00");
  }
}
