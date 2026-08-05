package com.splitz.expense.balancesource;

import com.splitz.expense.balance.DebtBalanceEngine;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Balance-source adapter for the {@link SimplificationScope#INTRA_GROUP} scope: reads the group's
 * Expenses and Settlement Allocations and runs them through the {@link DebtBalanceEngine} to derive
 * per-member net balances. Owns the "how the plan's intra-group balances are sourced" algorithm,
 * its repositories and its cost profile — in one place instead of inside the plan orchestrator's
 * scope branch.
 */
@Component
public class IntraGroupNetBalanceSource implements NetBalanceSource {

  private final ExpenseRepository expenseRepository;
  private final SettlementAllocationRepository settlementAllocationRepository;
  private final DebtBalanceEngine debtBalanceEngine;

  public IntraGroupNetBalanceSource(
      ExpenseRepository expenseRepository,
      SettlementAllocationRepository settlementAllocationRepository,
      DebtBalanceEngine debtBalanceEngine) {
    this.expenseRepository = expenseRepository;
    this.settlementAllocationRepository = settlementAllocationRepository;
    this.debtBalanceEngine = debtBalanceEngine;
  }

  @Override
  public SimplificationScope getSupportedScope() {
    return SimplificationScope.INTRA_GROUP;
  }

  @Override
  public NetBalanceResult resolve(Long groupId, List<Long> memberIds) {
    List<Expense> expenses = expenseRepository.findByGroupId(groupId);
    List<SettlementAllocation> allocations = settlementAllocationRepository.findByGroupId(groupId);
    return NetBalanceResult.builder()
        .netBalances(
            Collections.unmodifiableMap(
                debtBalanceEngine.calculateGroupBalances(memberIds, expenses, allocations)))
        .originalTransactionCount(expenses.size() + allocations.size())
        .build();
  }
}
