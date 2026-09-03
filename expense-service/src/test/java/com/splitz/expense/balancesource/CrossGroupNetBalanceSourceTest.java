package com.splitz.expense.balancesource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.splitz.expense.balance.FinancialLedgerEngine;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.repository.GroupMemberRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link CrossGroupNetBalanceSource} — the balance-source adapter for the CROSS_GROUP
 * scope. It delegates batch balance evaluation to the {@link FinancialLedgerEngine} and sums each
 * member's per-group balances from the batch result in a single round trip. Original transaction
 * count is zero (balances are derived from the cross-group ledger, not a single group's
 * transactions).
 */
class CrossGroupNetBalanceSourceTest {

  private GroupMemberRepository groupMemberRepository;
  private FinancialLedgerEngine financialLedgerEngine;
  private CrossGroupNetBalanceSource source;

  @BeforeEach
  void setUp() {
    groupMemberRepository = mock(GroupMemberRepository.class);
    financialLedgerEngine = mock(FinancialLedgerEngine.class);
    source = new CrossGroupNetBalanceSource(groupMemberRepository, financialLedgerEngine);
  }

  @Test
  @DisplayName("Should support the CROSS_GROUP scope")
  void shouldSupportCrossGroupScope() {
    assertThat(source.getSupportedScope()).isEqualTo(SimplificationScope.CROSS_GROUP);
  }

  @Test
  @DisplayName(
      "Should sum each member's per-group balances across all memberships from one batch engine call")
  void shouldAggregateBalancesAcrossAllMemberships() {
    Group group10 = Group.builder().id(10L).build();
    Group group20 = Group.builder().id(20L).build();
    Group group30 = Group.builder().id(30L).build();

    when(groupMemberRepository.findByUserIdIn(List.of(1L, 2L)))
        .thenReturn(
            List.of(
                GroupMember.builder().userId(1L).group(group10).build(),
                GroupMember.builder().userId(1L).group(group20).build(),
                GroupMember.builder().userId(2L).group(group10).build(),
                GroupMember.builder().userId(2L).group(group30).build()));

    Map<Long, Map<Long, BigDecimal>> engineBatch = new HashMap<>();
    engineBatch.put(
        1L,
        Map.of(
            10L, new BigDecimal("10.00"),
            20L, new BigDecimal("-3.50"),
            30L, BigDecimal.ZERO));
    engineBatch.put(
        2L,
        Map.of(
            10L, new BigDecimal("-10.00"),
            20L, BigDecimal.ZERO,
            30L, new BigDecimal("25.00")));

    when(financialLedgerEngine.calculateBalancesInGroups(any(), any())).thenReturn(engineBatch);

    NetBalanceResult result = source.resolve(10L, List.of(1L, 2L));

    assertThat(result.getOriginalTransactionCount()).isEqualTo(0);
    assertThat(result.getNetBalances().get(1L)).isEqualByComparingTo("6.50");
    assertThat(result.getNetBalances().get(2L)).isEqualByComparingTo("15.00");
  }

  @Test
  @DisplayName("Should return zero balances when members belong to no groups")
  void shouldReturnZeroWhenNoMemberships() {
    when(groupMemberRepository.findByUserIdIn(List.of(99L))).thenReturn(List.of());

    NetBalanceResult result = source.resolve(10L, List.of(99L));

    assertThat(result.getOriginalTransactionCount()).isEqualTo(0);
    assertThat(result.getNetBalances().get(99L)).isEqualByComparingTo("0.00");
  }
}
