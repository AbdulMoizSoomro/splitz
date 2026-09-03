package com.splitz.expense.balancesource;

import com.splitz.expense.balance.FinancialLedgerEngine;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Balance-source adapter for the {@link SimplificationScope#INTRA_GROUP} scope: delegates to the
 * deep {@link FinancialLedgerEngine} to derive per-member net balances.
 */
@Component
public class IntraGroupNetBalanceSource implements NetBalanceSource {

  private final ExpenseRepository expenseRepository;
  private final SettlementAllocationRepository settlementAllocationRepository;
  private final FinancialLedgerEngine financialLedgerEngine;

  public IntraGroupNetBalanceSource(
      ExpenseRepository expenseRepository,
      SettlementAllocationRepository settlementAllocationRepository,
      FinancialLedgerEngine financialLedgerEngine) {
    this.expenseRepository = expenseRepository;
    this.settlementAllocationRepository = settlementAllocationRepository;
    this.financialLedgerEngine = financialLedgerEngine;
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
                financialLedgerEngine.calculateGroupBalances(groupId, memberIds)))
        .originalTransactionCount(expenses.size() + allocations.size())
        .build();
  }
}
