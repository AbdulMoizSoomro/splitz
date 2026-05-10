package com.splitz.expense.calculator;

import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExactSplitStrategy implements SplitStrategy {
  @Override
  public SplitType getSupportedType() {
    return SplitType.EXACT;
  }

  @Override
  public List<SplitResult> calculate(
      BigDecimal totalAmount, List<SplitRequest> splitRequests, int scale) {
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
}
