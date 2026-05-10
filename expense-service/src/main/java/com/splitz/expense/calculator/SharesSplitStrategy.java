package com.splitz.expense.calculator;

import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SharesSplitStrategy implements SplitStrategy {
  @Override
  public SplitType getSupportedType() {
    return SplitType.SHARES;
  }

  @Override
  public List<SplitResult> calculate(
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
}
