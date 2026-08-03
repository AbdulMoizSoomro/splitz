package com.splitz.expense.balance;

import static org.assertj.core.api.Assertions.assertThat;

import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultDebtBalanceEngineTest {

  private DefaultDebtBalanceEngine engine;

  @BeforeEach
  void setUp() {
    engine = new DefaultDebtBalanceEngine();
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
}
