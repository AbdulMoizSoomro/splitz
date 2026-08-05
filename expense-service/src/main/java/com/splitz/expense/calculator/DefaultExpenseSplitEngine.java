package com.splitz.expense.calculator;

import com.splitz.expense.activity.ExpenseChange.SplitChange;
import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DefaultExpenseSplitEngine implements ExpenseSplitEngine {

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
      throw new InvalidSplitCalculationException("At least one split is required");
    }

    List<SplitResult> results =
        calculate(expense.getAmount(), splitType, splitRequests, expense.getCurrency());
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

  private List<SplitResult> calculate(
      BigDecimal totalAmount,
      SplitType splitType,
      List<SplitRequest> splitRequests,
      String currency) {
    if (splitRequests == null || splitRequests.isEmpty()) {
      throw new InvalidSplitCalculationException("At least one split is required");
    }

    int scale = getScale(currency);
    SplitType effectiveType = splitType != null ? splitType : SplitType.EQUAL;
    List<SplitResult> results =
        switch (effectiveType) {
          case EQUAL -> calculateEqual(totalAmount, splitRequests, scale);
          case EXACT -> calculateExact(totalAmount, splitRequests);
          case PERCENTAGE -> calculatePercentage(totalAmount, splitRequests, scale);
          case SHARES -> calculateShares(totalAmount, splitRequests, scale);
          case ADJUSTMENT -> calculateAdjustment(totalAmount, splitRequests, scale);
        };

    if (effectiveType != SplitType.EXACT) {
      handleRemainder(totalAmount, results);
    }

    return results;
  }

  private int getScale(String currency) {
    return switch (currency != null ? currency : "EUR") {
      case "JPY" -> 0;
      case "EUR", "USD" -> 2;
      case "KWD" -> 3;
      default -> 2;
    };
  }

  private void handleRemainder(BigDecimal totalAmount, List<SplitResult> results) {
    if (results == null || results.isEmpty()) {
      return;
    }
    BigDecimal sum =
        results.stream().map(SplitResult::shareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal remainder = totalAmount.subtract(sum);
    if (remainder.compareTo(BigDecimal.ZERO) != 0) {
      SplitResult first = results.get(0);
      results.set(
          0,
          new SplitResult(
              first.userId(),
              first.shareAmount().add(remainder),
              first.splitType(),
              first.splitValue()));
    }
  }

  private List<SplitResult> calculateEqual(
      BigDecimal totalAmount, List<SplitRequest> splitRequests, int scale) {
    List<SplitResult> results = new ArrayList<>();
    BigDecimal count = BigDecimal.valueOf(splitRequests.size());
    BigDecimal shareAmount = totalAmount.divide(count, scale, RoundingMode.HALF_UP);
    for (SplitRequest sr : splitRequests) {
      results.add(new SplitResult(sr.getUserId(), shareAmount, SplitType.EQUAL, null));
    }
    return results;
  }

  private List<SplitResult> calculateExact(
      BigDecimal totalAmount, List<SplitRequest> splitRequests) {
    List<SplitResult> results = new ArrayList<>();
    BigDecimal sum = BigDecimal.ZERO;
    for (SplitRequest sr : splitRequests) {
      if (sr.getSplitValue() == null) {
        throw new InvalidSplitCalculationException("Split value is required for EXACT split");
      }
      sum = sum.add(sr.getSplitValue());
      results.add(
          new SplitResult(sr.getUserId(), sr.getSplitValue(), SplitType.EXACT, sr.getSplitValue()));
    }
    if (sum.compareTo(totalAmount) != 0) {
      throw new InvalidSplitCalculationException("Sum of splits must equal total amount");
    }
    return results;
  }

  private List<SplitResult> calculatePercentage(
      BigDecimal totalAmount, List<SplitRequest> splitRequests, int scale) {
    List<SplitResult> results = new ArrayList<>();
    BigDecimal totalPercentage = BigDecimal.ZERO;
    for (SplitRequest sr : splitRequests) {
      if (sr.getSplitValue() == null) {
        throw new InvalidSplitCalculationException("Percentage required");
      }
      totalPercentage = totalPercentage.add(sr.getSplitValue());
      BigDecimal shareAmount =
          totalAmount
              .multiply(sr.getSplitValue())
              .divide(new BigDecimal("100"), scale, RoundingMode.HALF_UP);
      results.add(
          new SplitResult(sr.getUserId(), shareAmount, SplitType.PERCENTAGE, sr.getSplitValue()));
    }
    if (totalPercentage.compareTo(new BigDecimal("100")) != 0) {
      throw new InvalidSplitCalculationException("Percentage must sum to 100");
    }
    return results;
  }

  private List<SplitResult> calculateShares(
      BigDecimal totalAmount, List<SplitRequest> splitRequests, int scale) {
    List<SplitResult> results = new ArrayList<>();
    BigDecimal totalShares = BigDecimal.ZERO;
    for (SplitRequest sr : splitRequests) {
      if (sr.getSplitValue() == null || sr.getSplitValue().compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidSplitCalculationException("Positive shares required");
      }
      totalShares = totalShares.add(sr.getSplitValue());
    }
    for (SplitRequest sr : splitRequests) {
      BigDecimal shareAmount =
          totalAmount.multiply(sr.getSplitValue()).divide(totalShares, scale, RoundingMode.HALF_UP);
      results.add(
          new SplitResult(sr.getUserId(), shareAmount, SplitType.SHARES, sr.getSplitValue()));
    }
    return results;
  }

  private List<SplitResult> calculateAdjustment(
      BigDecimal totalAmount, List<SplitRequest> splitRequests, int scale) {
    List<SplitResult> results = new ArrayList<>();
    BigDecimal totalAdjustment = BigDecimal.ZERO;
    for (SplitRequest sr : splitRequests) {
      if (sr.getSplitValue() != null) {
        totalAdjustment = totalAdjustment.add(sr.getSplitValue());
      }
    }
    if (totalAdjustment.compareTo(BigDecimal.ZERO) != 0) {
      throw new InvalidSplitCalculationException("Adjustments must sum to zero");
    }
    BigDecimal count = BigDecimal.valueOf(splitRequests.size());
    BigDecimal baseShare = totalAmount.divide(count, scale, RoundingMode.HALF_UP);
    for (SplitRequest sr : splitRequests) {
      BigDecimal adj = sr.getSplitValue() != null ? sr.getSplitValue() : BigDecimal.ZERO;
      results.add(new SplitResult(sr.getUserId(), baseShare.add(adj), SplitType.ADJUSTMENT, adj));
    }
    return results;
  }
}
