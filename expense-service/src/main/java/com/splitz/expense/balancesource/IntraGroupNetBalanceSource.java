package com.splitz.expense.balancesource;

import com.splitz.expense.balance.FinancialLedgerEngine;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.PaymentRepository;
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
  private final PaymentRepository paymentRepository;
  private final FinancialLedgerEngine financialLedgerEngine;

  public IntraGroupNetBalanceSource(
      ExpenseRepository expenseRepository,
      PaymentRepository paymentRepository,
      FinancialLedgerEngine financialLedgerEngine) {
    this.expenseRepository = expenseRepository;
    this.paymentRepository = paymentRepository;
    this.financialLedgerEngine = financialLedgerEngine;
  }

  @Override
  public SimplificationScope getSupportedScope() {
    return SimplificationScope.INTRA_GROUP;
  }

  @Override
  public NetBalanceResult resolve(Long groupId, List<Long> memberIds) {
    List<Expense> expenses = expenseRepository.findByGroupId(groupId);
    List<Payment> payments = paymentRepository.findByGroupId(groupId);
    return NetBalanceResult.builder()
        .netBalances(
            Collections.unmodifiableMap(
                financialLedgerEngine.calculateGroupBalances(groupId, memberIds)))
        .originalTransactionCount(expenses.size() + payments.size())
        .build();
  }
}
