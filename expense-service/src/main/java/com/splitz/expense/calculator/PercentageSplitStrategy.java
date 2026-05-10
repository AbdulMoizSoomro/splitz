package com.splitz.expense.calculator;

import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PercentageSplitStrategy implements SplitStrategy {
  @Override
  public SplitType getSupportedType() {
    return SplitType.PERCENTAGE;
  }

  @Override
  public List<SplitResult> calculate(
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
}
