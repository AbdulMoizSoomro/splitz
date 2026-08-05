package com.splitz.expense.balance;

import com.splitz.expense.model.Expense;
import com.splitz.expense.model.SettlementAllocation;
import java.math.BigDecimal;
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

  /** Calculates the net global (non-group) friendship settlement balance between two users. */
  BigDecimal calculateGlobalSettlementBalance(Long userId, Long friendId);

  /** Calculates a user's total global (non-group) friendship settlement balance. */
  BigDecimal calculateUserGlobalSettlementBalance(Long userId);
}
