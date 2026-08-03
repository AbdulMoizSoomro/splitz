package com.splitz.expense.netting;

import com.splitz.expense.model.DebtSimplificationPlan;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Deep Debt-Netting Engine. The single home of the greedy priority-queue netting algorithm for debt
 * simplification. Produces a deterministic {@link DebtSimplificationPlan} given net balances,
 * honouring governance opt-outs, rounding and deterministic ordering. Both the suggested-plan
 * endpoint and the group-balances simplified-debts view route through this one module.
 */
public interface DebtNettingEngine {

  /**
   * Nets the given net balances into a minimal, deterministic set of suggested transfers.
   *
   * @param groupId ID of the group this plan belongs to
   * @param netBalances Map of userId -&gt; net balance (positive = creditor, negative = debtor)
   * @param optedOutUserIds Set of user IDs excluded from simplification
   * @param usernames Map of userId -&gt; username (optional, for display)
   * @param originalTransactionCount Count of raw/un-simplified transactions before netting
   * @return computed {@link DebtSimplificationPlan} domain model
   */
  DebtSimplificationPlan simplifyDebts(
      Long groupId,
      Map<Long, BigDecimal> netBalances,
      Set<Long> optedOutUserIds,
      Map<Long, String> usernames,
      int originalTransactionCount);
}
