package com.splitz.expense.calculator;

import com.splitz.expense.activity.ExpenseChange.SplitChange;
import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.SplitType;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultExpenseSplitEngine implements ExpenseSplitEngine {

  private final SplitCalculator splitCalculator;

  @Override
  public List<ExpenseSplit> applyInitialSplits(
      Expense expense, List<SplitRequest> splitRequests, SplitType splitType) {
    return calculateSplits(expense, splitRequests, splitType);
  }

  @Override
  public void reconcileSplits(
      Expense expense,
      SplitChange splitChange,
      List<SplitRequest> requestedSplits,
      SplitType requestedSplitType) {
    if (splitChange == SplitChange.NONE) {
      return;
    }

    if (splitChange == SplitChange.MODIFIED) {
      SplitType splitType =
          requestedSplitType != null
              ? requestedSplitType
              : (expense.getSplits().isEmpty()
                  ? SplitType.EQUAL
                  : expense.getSplits().get(0).getSplitType());
      List<ExpenseSplit> newSplits = calculateSplits(expense, requestedSplits, splitType);
      expense.getSplits().clear();
      expense.getSplits().addAll(newSplits);
    } else if (splitChange == SplitChange.RECALCULATED) {
      SplitType splitType =
          expense.getSplits().isEmpty()
              ? SplitType.EQUAL
              : expense.getSplits().get(0).getSplitType();
      List<SplitRequest> splitRequests =
          expense.getSplits().stream()
              .map(
                  s ->
                      SplitRequest.builder()
                          .userId(s.getUserId())
                          .splitValue(s.getSplitValue())
                          .build())
              .collect(Collectors.toList());
      List<ExpenseSplit> updatedSplits = calculateSplits(expense, splitRequests, splitType);
      expense.getSplits().clear();
      expense.getSplits().addAll(updatedSplits);
    }
  }

  private List<ExpenseSplit> calculateSplits(
      Expense expense, List<SplitRequest> splitRequests, SplitType splitType) {
    if (splitRequests == null || splitRequests.isEmpty()) {
      throw new IllegalArgumentException("At least one split is required");
    }

    List<SplitResult> results =
        splitCalculator.calculate(
            expense.getAmount(), splitType, splitRequests, expense.getCurrency());
    return results.stream()
        .map(
            r ->
                ExpenseSplit.builder()
                    .expense(expense)
                    .userId(r.userId())
                    .splitType(r.splitType())
                    .splitValue(r.splitValue())
                    .shareAmount(r.shareAmount())
                    .build())
        .collect(Collectors.toList());
  }
}
