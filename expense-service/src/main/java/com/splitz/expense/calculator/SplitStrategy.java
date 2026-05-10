package com.splitz.expense.calculator;

import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.util.List;

public interface SplitStrategy {
  SplitType getSupportedType();

  List<SplitResult> calculate(BigDecimal totalAmount, List<SplitRequest> splitRequests, int scale);
}
