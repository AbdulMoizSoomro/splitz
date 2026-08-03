package com.splitz.expense.allocator;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/** One group in which the payer owes the payee, as a positive owed amount. */
@Getter
@Builder
public final class GroupDebt {

  private final Long groupId;

  /** Positive magnitude the payer owes in this group. */
  private final BigDecimal owedAmount;
}
