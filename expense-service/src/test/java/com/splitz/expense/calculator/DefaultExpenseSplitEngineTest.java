package com.splitz.expense.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.splitz.expense.activity.ExpenseChange.SplitChange;
import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultExpenseSplitEngineTest {

  private ExpenseSplitEngine splitEngine;
  private Expense expense;

  @BeforeEach
  void setUp() {
    splitEngine = new DefaultExpenseSplitEngine();

    Group group = Group.builder().id(1L).name("Test Group").build();
    expense =
        Expense.builder()
            .id(10L)
            .group(group)
            .description("Dinner")
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .splits(new ArrayList<>())
            .build();
  }

  @Test
  void applyInitialSplits_EqualSplit_CalculatesCorrectShareAmounts() {
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).build(), SplitRequest.builder().userId(2L).build());

    List<ExpenseSplit> splits = splitEngine.applyInitialSplits(expense, requests, SplitType.EQUAL);

    assertEquals(2, splits.size());
    assertEquals(new BigDecimal("50.00"), splits.get(0).getShareAmount());
    assertEquals(new BigDecimal("50.00"), splits.get(1).getShareAmount());
  }

  @Test
  void applyInitialSplits_ExactSplit_CalculatesCorrectShareAmounts() {
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("40.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("60.00")).build());

    List<ExpenseSplit> splits = splitEngine.applyInitialSplits(expense, requests, SplitType.EXACT);

    assertEquals(2, splits.size());
    assertEquals(new BigDecimal("40.00"), splits.get(0).getShareAmount());
    assertEquals(new BigDecimal("60.00"), splits.get(1).getShareAmount());
  }

  @Test
  void applyInitialSplits_ExactSplit_MismatchAmount_ThrowsException() {
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("40.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("50.00")).build());

    assertThrows(
        InvalidSplitCalculationException.class,
        () -> splitEngine.applyInitialSplits(expense, requests, SplitType.EXACT));
  }

  @Test
  void applyInitialSplits_PercentageSplit_CalculatesCorrectShareAmounts() {
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("25.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("75.00")).build());

    List<ExpenseSplit> splits =
        splitEngine.applyInitialSplits(expense, requests, SplitType.PERCENTAGE);

    assertEquals(2, splits.size());
    assertEquals(new BigDecimal("25.00"), splits.get(0).getShareAmount());
    assertEquals(new BigDecimal("75.00"), splits.get(1).getShareAmount());
  }

  @Test
  void applyInitialSplits_PercentageSplit_Not100_ThrowsException() {
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("50.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("40.00")).build());

    assertThrows(
        InvalidSplitCalculationException.class,
        () -> splitEngine.applyInitialSplits(expense, requests, SplitType.PERCENTAGE));
  }

  @Test
  void applyInitialSplits_SharesSplit_CalculatesCorrectShareAmounts() {
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("1.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("3.00")).build());

    List<ExpenseSplit> splits = splitEngine.applyInitialSplits(expense, requests, SplitType.SHARES);

    assertEquals(2, splits.size());
    assertEquals(new BigDecimal("25.00"), splits.get(0).getShareAmount());
    assertEquals(new BigDecimal("75.00"), splits.get(1).getShareAmount());
  }

  @Test
  void applyInitialSplits_SharesSplit_InvalidShares_ThrowsException() {
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("0")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("3.00")).build());

    assertThrows(
        InvalidSplitCalculationException.class,
        () -> splitEngine.applyInitialSplits(expense, requests, SplitType.SHARES));
  }

  @Test
  void applyInitialSplits_AdjustmentSplit_CalculatesCorrectShareAmounts() {
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("10.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("-10.00")).build());

    List<ExpenseSplit> splits =
        splitEngine.applyInitialSplits(expense, requests, SplitType.ADJUSTMENT);

    assertEquals(2, splits.size());
    assertEquals(new BigDecimal("60.00"), splits.get(0).getShareAmount());
    assertEquals(new BigDecimal("40.00"), splits.get(1).getShareAmount());
  }

  @Test
  void applyInitialSplits_AdjustmentSplit_NonZeroSum_ThrowsException() {
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("10.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("-5.00")).build());

    assertThrows(
        InvalidSplitCalculationException.class,
        () -> splitEngine.applyInitialSplits(expense, requests, SplitType.ADJUSTMENT));
  }

  @Test
  void applyInitialSplits_MultiCurrency_JPY_HandlesZeroScale() {
    expense.setAmount(new BigDecimal("100"));
    expense.setCurrency("JPY");
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).build(),
            SplitRequest.builder().userId(2L).build(),
            SplitRequest.builder().userId(3L).build());

    List<ExpenseSplit> splits = splitEngine.applyInitialSplits(expense, requests, SplitType.EQUAL);

    assertEquals(3, splits.size());
    assertEquals(new BigDecimal("34"), splits.get(0).getShareAmount());
    assertEquals(new BigDecimal("33"), splits.get(1).getShareAmount());
    assertEquals(new BigDecimal("33"), splits.get(2).getShareAmount());
  }

  @Test
  void applyInitialSplits_MultiCurrency_KWD_HandlesThreeScale() {
    expense.setAmount(new BigDecimal("10.000"));
    expense.setCurrency("KWD");
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).build(),
            SplitRequest.builder().userId(2L).build(),
            SplitRequest.builder().userId(3L).build());

    List<ExpenseSplit> splits = splitEngine.applyInitialSplits(expense, requests, SplitType.EQUAL);

    assertEquals(3, splits.size());
    assertEquals(new BigDecimal("3.334"), splits.get(0).getShareAmount());
    assertEquals(new BigDecimal("3.333"), splits.get(1).getShareAmount());
    assertEquals(new BigDecimal("3.333"), splits.get(2).getShareAmount());
  }

  @Test
  void applyInitialSplits_EmptyRequests_ThrowsInvalidSplitCalculationException() {
    assertThrows(
        InvalidSplitCalculationException.class,
        () -> splitEngine.applyInitialSplits(expense, List.of(), SplitType.EQUAL));
  }

  @Test
  void reconcileSplits_SplitChangeNone_DoesNotModifySplits() {
    ExpenseSplit initialSplit =
        ExpenseSplit.builder().userId(1L).shareAmount(new BigDecimal("100.00")).build();
    expense.getSplits().add(initialSplit);

    splitEngine.reconcileSplits(expense, SplitChange.NONE, List.of(), null);

    assertEquals(1, expense.getSplits().size());
    assertEquals(initialSplit, expense.getSplits().get(0));
  }

  @Test
  void reconcileSplits_SplitChangeModified_UpdatesExpenseSplits() {
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("60.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("40.00")).build());

    splitEngine.reconcileSplits(expense, SplitChange.MODIFIED, requests, SplitType.EXACT);

    assertEquals(2, expense.getSplits().size());
    assertEquals(new BigDecimal("60.00"), expense.getSplits().get(0).getShareAmount());
    assertEquals(new BigDecimal("40.00"), expense.getSplits().get(1).getShareAmount());
  }

  @Test
  void reconcileSplits_SplitChangeRecalculated_RecalculatesWithNewAmount() {
    ExpenseSplit split1 = ExpenseSplit.builder().userId(1L).splitType(SplitType.EQUAL).build();
    ExpenseSplit split2 = ExpenseSplit.builder().userId(2L).splitType(SplitType.EQUAL).build();
    expense.getSplits().addAll(List.of(split1, split2));

    // Amount changed to 120.00
    expense.setAmount(new BigDecimal("120.00"));

    splitEngine.reconcileSplits(expense, SplitChange.RECALCULATED, null, null);

    assertEquals(2, expense.getSplits().size());
    assertEquals(new BigDecimal("60.00"), expense.getSplits().get(0).getShareAmount());
    assertEquals(new BigDecimal("60.00"), expense.getSplits().get(1).getShareAmount());
  }
}
