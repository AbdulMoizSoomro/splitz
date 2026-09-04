package com.splitz.expense.repository;

import java.math.BigDecimal;

/** Projection interface for user balance aggregations. */
public interface UserBalanceAggregate {

  Long getUserId();

  BigDecimal getBalance();
}
