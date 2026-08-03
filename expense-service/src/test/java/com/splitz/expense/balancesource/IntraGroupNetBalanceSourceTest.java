package com.splitz.expense.balancesource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.balance.DebtBalanceEngine;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link IntraGroupNetBalanceSource} — the balance-source adapter for the INTRA_GROUP
 * scope. Its home for the "read the group's Expenses + Settlement Allocations and run them through
 * the {@link DebtBalanceEngine}" algorithm, including counting the original transactions the plan's
 * {@code originalTransactionCount} is derived from.
 */
class IntraGroupNetBalanceSourceTest {

  private ExpenseRepository expenseRepository;
  private SettlementAllocationRepository settlementAllocationRepository;
  private DebtBalanceEngine debtBalanceEngine;
  private IntraGroupNetBalanceSource source;

  private static final Long GROUP_ID = 10L;

  @BeforeEach
  void setUp() {
    expenseRepository = mock(ExpenseRepository.class);
    settlementAllocationRepository = mock(SettlementAllocationRepository.class);
    debtBalanceEngine = mock(DebtBalanceEngine.class);
    source =
        new IntraGroupNetBalanceSource(
            expenseRepository, settlementAllocationRepository, debtBalanceEngine);
  }

  @Test
  @DisplayName("Should support the INTRA_GROUP scope")
  void shouldSupportIntraGroupScope() {
    assertThat(source.getSupportedScope()).isEqualTo(SimplificationScope.INTRA_GROUP);
  }

  @Test
  @DisplayName(
      "Should source balances from the group's expenses and allocations and count them as originals")
  void shouldResolveFromGroupExpensesAndAllocations() {
    List<Long> memberIds = List.of(1L, 2L, 3L);

    List<Expense> expenses =
        List.of(
            Expense.builder().paidBy(1L).amount(new BigDecimal("100.00")).build(),
            Expense.builder().paidBy(2L).amount(new BigDecimal("50.00")).build());
    List<SettlementAllocation> allocations =
        List.of(SettlementAllocation.builder().amount(new BigDecimal("20.00")).build());

    when(expenseRepository.findByGroupId(GROUP_ID)).thenReturn(expenses);
    when(settlementAllocationRepository.findByGroupId(GROUP_ID)).thenReturn(allocations);

    Map<Long, BigDecimal> balances = new HashMap<>();
    balances.put(1L, new BigDecimal("30.00"));
    balances.put(2L, new BigDecimal("-30.00"));
    when(debtBalanceEngine.calculateGroupBalances(memberIds, expenses, allocations))
        .thenReturn(balances);

    NetBalanceResult result = source.resolve(GROUP_ID, memberIds);

    assertThat(result.getNetBalances()).isEqualTo(balances);
    // Original transaction count is the raw expenses plus allocations feeding the engine.
    assertThat(result.getOriginalTransactionCount())
        .isEqualTo(expenses.size() + allocations.size());
    verify(expenseRepository).findByGroupId(GROUP_ID);
    verify(settlementAllocationRepository).findByGroupId(GROUP_ID);
    verify(debtBalanceEngine).calculateGroupBalances(memberIds, expenses, allocations);
  }
}
