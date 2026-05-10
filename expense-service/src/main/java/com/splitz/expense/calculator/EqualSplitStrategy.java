package com.splitz.expense.calculator;

import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EqualSplitStrategy implements SplitStrategy {
  @Override
  public SplitType getSupportedType() {
    return SplitType.EQUAL;
  }

  @Override
  public List<SplitResult> calculate(
      BigDecimal totalAmount, List<SplitRequest> splitRequests, int scale) {
    List<SplitResult> results = new ArrayList<>();
    BigDecimal count = BigDecimal.valueOf(splitRequests.size());
    BigDecimal shareAmount = totalAmount.divide(count, scale, RoundingMode.HALF_UP);

    for (SplitRequest sr : splitRequests) {
      results.add(new SplitResult(sr.getUserId(), shareAmount, SplitType.EQUAL, null));
    }
    return results;
  }
}
