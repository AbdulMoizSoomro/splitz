package com.splitz.expense.calculator;

import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;

public record SplitResult(
    Long userId, BigDecimal shareAmount, SplitType splitType, BigDecimal splitValue) {}
