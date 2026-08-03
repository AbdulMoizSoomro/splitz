package com.splitz.expense.balancesource;

import com.splitz.expense.model.SimplificationScope;
import java.util.List;

/**
 * Balance-source adapter seam for the Suggested Settlement Plan. Each {@link SimplificationScope}
 * has its own source: one that derives per-member net balances inside a single group ({@code
 * IntraGroupNetBalanceSource}) and one that aggregates a member's net balance across all of their
 * group memberships ({@code CrossGroupNetBalanceSource}). The plan flow resolves settings, picks
 * the source for the scope, and delegates balance sourcing to it — so each algorithm's
 * repositories, cost profile and failure modes live behind one adapter instead of inside a branch
 * of the orchestrator.
 */
public interface NetBalanceSource {

  /**
   * The {@link SimplificationScope} this adapter sources balances for. Used by the {@link
   * NetBalanceSourceRegistry} to route a scope to its adapter.
   */
  SimplificationScope getSupportedScope();

  /**
   * Resolves the net balances of the given members, plus a count of the original (un-simplified)
   * transactions that the balances were derived from.
   *
   * @param groupId group the plan is being computed for (used by the intra-group adapter)
   * @param memberIds members whose balances to resolve
   * @return the resolved net balances and original transaction count
   */
  NetBalanceResult resolve(Long groupId, List<Long> memberIds);
}
