package com.splitz.expense.calculator;

public class InvalidSplitCalculationException extends IllegalArgumentException {
  public InvalidSplitCalculationException(String message) {
    super(message);
  }
}
