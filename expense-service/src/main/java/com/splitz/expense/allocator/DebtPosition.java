package com.splitz.expense.allocator;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * The input the settlement-allocation decision reads from: the payer's eligible group debts,
 * resolved once up front instead of re-derived inside every allocator. A deep value object — a
 * caller (or test) supplies a plain {@link DebtPosition} and the allocator does the rest.
 */
@Getter
@Builder
public final class DebtPosition {

  private final Long payerId;
  private final Long payeeId;

  /** The groups where the payer owes the payee, ordered deterministically (ascending group id). */
  private final List<GroupDebt> debts;
}
