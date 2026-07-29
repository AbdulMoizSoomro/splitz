package com.splitz.expense.service;

import com.splitz.expense.model.DebtSimplificationPlan;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public interface GreedyDebtSimplifier {

  /**
   * Generates a deterministic debt simplification plan for a group given net balances.
   *
   * @param groupId ID of the group
   * @param netBalances Map of userId -> net balance (positive = creditor, negative = debtor)
   * @param optedOutUserIds Set of user IDs who have opted out of debt simplification
   * @param usernames Map of userId -> username (optional for display)
   * @param originalTransactionCount Count of raw/un-simplified transactions before netting
   * @return Computed DebtSimplificationPlan domain model
   */
  DebtSimplificationPlan simplifyDebts(
      Long groupId,
      Map<Long, BigDecimal> netBalances,
      Set<Long> optedOutUserIds,
      Map<Long, String> usernames,
      int originalTransactionCount);

  /** Overloaded helper method where originalTransactionCount is omitted (defaults to 0). */
  DebtSimplificationPlan simplifyDebts(
      Long groupId,
      Map<Long, BigDecimal> netBalances,
      Set<Long> optedOutUserIds,
      Map<Long, String> usernames);
}
