package com.splitz.expense.balance;

import com.splitz.expense.dto.DebtDTO;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.SettlementAllocation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface DebtBalanceEngine {
  /** Calculates the net balances of each member in a group, based on expenses and settlements. */
  Map<Long, BigDecimal> calculateGroupBalances(
      List<Long> memberIds, List<Expense> expenses, List<SettlementAllocation> allocations);

  /** Runs the greedy priority-queue debt-simplification algorithm on the provided balances. */
  List<DebtDTO> simplifyDebts(Map<Long, BigDecimal> balances, Map<Long, String> usernames);

  /** Calculates the net balance between two users in a group. */
  BigDecimal calculateNetBalanceInGroup(
      Long userId, Long friendId, List<Expense> expenses, List<SettlementAllocation> allocations);
}
