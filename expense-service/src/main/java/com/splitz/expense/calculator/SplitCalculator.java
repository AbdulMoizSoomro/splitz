package com.splitz.expense.calculator;

import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SplitCalculator {

  public List<SplitResult> calculate(
      BigDecimal totalAmount,
      SplitType splitType,
      List<SplitRequest> splitRequests,
      String currency) {

    if (splitRequests == null || splitRequests.isEmpty()) {
      throw new InvalidSplitCalculationException("At least one split is required");
    }

    int scale = getScale(currency);

    List<SplitResult> results =
        switch (splitType) {
          case EXACT -> calculateExactSplits(totalAmount, splitRequests);
          case EQUAL -> calculateEqualSplits(totalAmount, splitRequests, scale);
          case PERCENTAGE -> calculatePercentageSplits(totalAmount, splitRequests, scale);
          case SHARES -> calculateSharesSplits(totalAmount, splitRequests, scale);
          case ADJUSTMENT -> calculateAdjustmentSplits(totalAmount, splitRequests, scale);
        };

    if (splitType != SplitType.EXACT) {
      handleRemainder(totalAmount, results);
    }

    return results;
  }

  private List<SplitResult> calculateExactSplits(
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

  private void handleRemainder(BigDecimal totalAmount, List<SplitResult> results) {
    BigDecimal sum =
        results.stream().map(SplitResult::shareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal remainder = totalAmount.subtract(sum);
    if (remainder.compareTo(BigDecimal.ZERO) != 0 && !results.isEmpty()) {
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

  private int getScale(String currency) {
    return switch (currency) {
      case "JPY" -> 0;
      case "EUR", "USD" -> 2;
      case "KWD" -> 3;
      default -> 2;
    };
  }

  private List<SplitResult> calculateEqualSplits(
      BigDecimal totalAmount, List<SplitRequest> splitRequests, int scale) {
    List<SplitResult> results = new ArrayList<>();
    BigDecimal count = BigDecimal.valueOf(splitRequests.size());
    BigDecimal shareAmount = totalAmount.divide(count, scale, RoundingMode.HALF_UP);

    for (SplitRequest sr : splitRequests) {
      results.add(new SplitResult(sr.getUserId(), shareAmount, SplitType.EQUAL, null));
    }
    return results;
  }

  private List<SplitResult> calculatePercentageSplits(
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

  private List<SplitResult> calculateSharesSplits(
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

  private List<SplitResult> calculateAdjustmentSplits(
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
