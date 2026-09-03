package com.splitz.expense.balancesource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.balance.FinancialLedgerEngine;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.PaymentType;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntraGroupNetBalanceSourceTest {

  private ExpenseRepository expenseRepository;
  private PaymentRepository paymentRepository;
  private FinancialLedgerEngine financialLedgerEngine;
  private IntraGroupNetBalanceSource source;

  private static final Long GROUP_ID = 10L;

  @BeforeEach
  void setUp() {
    expenseRepository = mock(ExpenseRepository.class);
    paymentRepository = mock(PaymentRepository.class);
    financialLedgerEngine = mock(FinancialLedgerEngine.class);
    source =
        new IntraGroupNetBalanceSource(expenseRepository, paymentRepository, financialLedgerEngine);
  }

  @Test
  @DisplayName("Should support the INTRA_GROUP scope")
  void shouldSupportIntraGroupScope() {
    assertThat(source.getSupportedScope()).isEqualTo(SimplificationScope.INTRA_GROUP);
  }

  @Test
  @DisplayName(
      "Should source balances from the group's expenses and payments and count them as originals")
  void shouldResolveFromGroupExpensesAndPayments() {
    List<Long> memberIds = List.of(1L, 2L, 3L);

    List<Expense> expenses =
        List.of(
            Expense.builder().paidBy(1L).amount(new BigDecimal("100.00")).build(),
            Expense.builder().paidBy(2L).amount(new BigDecimal("50.00")).build());
    List<Payment> payments =
        List.of(
            Payment.builder()
                .type(PaymentType.GROUP)
                .groupId(GROUP_ID)
                .amount(new BigDecimal("20.00"))
                .status(SettlementStatus.COMPLETED)
                .build());

    when(expenseRepository.findByGroupId(GROUP_ID)).thenReturn(expenses);
    when(paymentRepository.findByGroupId(GROUP_ID)).thenReturn(payments);

    Map<Long, BigDecimal> balances = new HashMap<>();
    balances.put(1L, new BigDecimal("30.00"));
    balances.put(2L, new BigDecimal("-30.00"));
    when(financialLedgerEngine.calculateGroupBalances(GROUP_ID, memberIds)).thenReturn(balances);

    NetBalanceResult result = source.resolve(GROUP_ID, memberIds);

    assertThat(result.getNetBalances()).isEqualTo(balances);
    assertThat(result.getOriginalTransactionCount()).isEqualTo(expenses.size() + payments.size());
    verify(expenseRepository).findByGroupId(GROUP_ID);
    verify(paymentRepository).findByGroupId(GROUP_ID);
    verify(financialLedgerEngine).calculateGroupBalances(GROUP_ID, memberIds);
  }
}
