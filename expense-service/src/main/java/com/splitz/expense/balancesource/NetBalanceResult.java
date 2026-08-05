package com.splitz.expense.balancesource;

import java.math.BigDecimal;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * The outcome of a {@link NetBalanceSource}: the members' net balances (positive = creditor,
 * negative = debtor) plus the number of original/raw transactions those balances were derived from
 * (used as the plan's {@code originalTransactionCount}). A plain value object, so a source — or a
 * test stub — can produce one without any framework wiring.
 */
@Getter
@Builder
public final class NetBalanceResult {

  private final Map<Long, BigDecimal> netBalances;
  private final int originalTransactionCount;
}
