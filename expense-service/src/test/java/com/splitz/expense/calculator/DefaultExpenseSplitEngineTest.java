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

  private DefaultExpenseSplitEngine splitEngine;
  private Expense expense;

  @BeforeEach
  void setUp() {
    SplitCalculator splitCalculator =
        new SplitCalculator(
            List.of(
                new EqualSplitStrategy(),
                new ExactSplitStrategy(),
                new PercentageSplitStrategy(),
                new SharesSplitStrategy(),
                new AdjustmentSplitStrategy()),
            new RemainderHandler());
    splitEngine = new DefaultExpenseSplitEngine(splitCalculator);

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
  void applyInitialSplits_EmptyRequests_ThrowsIllegalArgument() {
    assertThrows(
        IllegalArgumentException.class,
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
