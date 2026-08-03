package com.splitz.expense.model;

/**
 * Scope of debt simplification for a group, per Wayfinder decision #65.
 *
 * <p>INTRA_GROUP (default): simplifies debts using net balances within the single group.
 * CROSS_GROUP: simplifies debts using each member's net balance aggregated across all groups they
 * participate in.
 */
public enum SimplificationScope {
  INTRA_GROUP,
  CROSS_GROUP
}
