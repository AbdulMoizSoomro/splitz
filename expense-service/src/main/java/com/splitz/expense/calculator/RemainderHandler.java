package com.splitz.expense.calculator;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RemainderHandler {

  public void handle(BigDecimal totalAmount, List<SplitResult> results) {
    if (results == null || results.isEmpty()) {
      return;
    }

    BigDecimal sum =
        results.stream().map(SplitResult::shareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal remainder = totalAmount.subtract(sum);

    if (remainder.compareTo(BigDecimal.ZERO) != 0) {
      // For now, give the remainder to the first user
      // In more advanced logic, we could distribute it differently
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
}
