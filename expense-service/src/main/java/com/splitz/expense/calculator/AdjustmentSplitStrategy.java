package com.splitz.expense.calculator;

import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdjustmentSplitStrategy implements SplitStrategy {
  @Override
  public SplitType getSupportedType() {
    return SplitType.ADJUSTMENT;
  }

  @Override
  public List<SplitResult> calculate(
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
