package com.splitz.expense.calculator;

import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SplitCalculator {

  private final Map<SplitType, SplitStrategy> strategies;
  private final RemainderHandler remainderHandler;

  public SplitCalculator(List<SplitStrategy> strategyList, RemainderHandler remainderHandler) {
    this.strategies =
        strategyList.stream().collect(Collectors.toMap(SplitStrategy::getSupportedType, s -> s));
    this.remainderHandler = remainderHandler;
  }

  public List<SplitResult> calculate(
      BigDecimal totalAmount,
      SplitType splitType,
      List<SplitRequest> splitRequests,
      String currency) {

    if (splitRequests == null || splitRequests.isEmpty()) {
      throw new InvalidSplitCalculationException("At least one split is required");
    }

    SplitStrategy strategy = strategies.get(splitType);
    if (strategy == null) {
      throw new InvalidSplitCalculationException("Unsupported split type: " + splitType);
    }

    int scale = getScale(currency);

    List<SplitResult> results = strategy.calculate(totalAmount, splitRequests, scale);

    if (splitType != SplitType.EXACT) {
      remainderHandler.handle(totalAmount, results);
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
}
