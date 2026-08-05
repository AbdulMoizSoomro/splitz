package com.splitz.expense.balancesource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.splitz.expense.balance.DebtBalanceEngine;
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
 * scope. It delegates batch balance evaluation to the {@link DebtBalanceEngine} and sums each
 * member's per-group balances from the batch result in a single round trip. Original transaction
 * count is zero (balances are derived from the cross-group ledger, not a single group's
 * transactions).
 */
class CrossGroupNetBalanceSourceTest {

  private GroupMemberRepository groupMemberRepository;
  private DebtBalanceEngine debtBalanceEngine;
  private CrossGroupNetBalanceSource source;

  @BeforeEach
  void setUp() {
    groupMemberRepository = mock(GroupMemberRepository.class);
    debtBalanceEngine = mock(DebtBalanceEngine.class);
    source = new CrossGroupNetBalanceSource(groupMemberRepository, debtBalanceEngine);
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

    Map<Long, Map<Long, BigDecimal>> batch = new HashMap<>();
    batch.put(1L, Map.of(10L, new BigDecimal("-30.00"), 20L, new BigDecimal("-20.00")));
    batch.put(2L, Map.of(10L, new BigDecimal("30.00"), 30L, new BigDecimal("20.00")));
    when(debtBalanceEngine.calculateBalancesInGroups(any(), any())).thenReturn(batch);

    NetBalanceResult result = source.resolve(1L, List.of(1L, 2L));

    Map<Long, BigDecimal> expected =
        Map.of(1L, new BigDecimal("-50.00"), 2L, new BigDecimal("50.00"));
    assertThat(result.getNetBalances()).isEqualTo(expected);
    assertThat(result.getOriginalTransactionCount()).isZero();
  }

  @Test
  @DisplayName("Should resolve a member with no memberships to a zero balance")
  void shouldResolveZeroBalanceForMemberWithoutMemberships() {
    when(groupMemberRepository.findByUserIdIn(List.of(9L))).thenReturn(List.of());
    when(debtBalanceEngine.calculateBalancesInGroups(any(), any()))
        .thenReturn(Map.of(9L, Map.of()));

    NetBalanceResult result = source.resolve(1L, List.of(9L));

    assertThat(result.getNetBalances().get(9L)).isEqualByComparingTo("0.00");
  }
}
