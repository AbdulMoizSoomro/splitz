package com.splitz.expense.balance;

import com.splitz.expense.model.Expense;
import com.splitz.expense.model.SettlementAllocation;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface DebtBalanceEngine {
  /** Calculates the net balances of each member in a group, based on expenses and settlements. */
  Map<Long, BigDecimal> calculateGroupBalances(
      List<Long> memberIds, List<Expense> expenses, List<SettlementAllocation> allocations);

  /** Calculates the net balance between two users in a group. */
  BigDecimal calculateNetBalanceInGroup(
      Long userId, Long friendId, List<Expense> expenses, List<SettlementAllocation> allocations);

  /** Calculates a user's net balance within a specific group using financial aggregates. */
  BigDecimal calculateUserBalanceInGroup(Long userId, Long groupId);

  /**
   * Calculates the net balance of each userId in each groupId in a single batch of aggregate
   * queries. Every (userId, groupId) pair in the inputs is present in the result, zero-filled when
   * no transactions exist.
   */
  Map<Long, Map<Long, BigDecimal>> calculateBalancesInGroups(
      Collection<Long> userIds, Collection<Long> groupIds);

  /** Calculates the net global (non-group) friendship settlement balance between two users. */
  BigDecimal calculateGlobalSettlementBalance(Long userId, Long friendId);

  /** Calculates a user's total global (non-group) friendship settlement balance. */
  BigDecimal calculateUserGlobalSettlementBalance(Long userId);
}
