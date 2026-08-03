package com.splitz.expense.balancesource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.service.BalanceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link CrossGroupNetBalanceSource} — the balance-source adapter for the CROSS_GROUP
 * scope. Its home for the "walk each member's group memberships and sum per-group balances"
 * algorithm, including the cost profile of one per-group balance call per membership. Original
 * transaction count is zero (balances are derived from the cross-group ledger, not a single group's
 * transactions).
 */
class CrossGroupNetBalanceSourceTest {

  private GroupMemberRepository groupMemberRepository;
  private BalanceService balanceService;
  private CrossGroupNetBalanceSource source;

  @BeforeEach
  void setUp() {
    groupMemberRepository = mock(GroupMemberRepository.class);
    balanceService = mock(BalanceService.class);
    source = new CrossGroupNetBalanceSource(groupMemberRepository, balanceService);
  }

  @Test
  @DisplayName("Should support the CROSS_GROUP scope")
  void shouldSupportCrossGroupScope() {
    assertThat(source.getSupportedScope()).isEqualTo(SimplificationScope.CROSS_GROUP);
  }

  @Test
  @DisplayName(
      "Should sum each member's per-group balances across all their group memberships, with zero originals")
  void shouldAggregateBalancesAcrossAllMemberships() {
    Group group10 = Group.builder().id(10L).build();
    Group group20 = Group.builder().id(20L).build();
    Group group30 = Group.builder().id(30L).build();

    when(groupMemberRepository.findByUserId(1L))
        .thenReturn(
            List.of(
                GroupMember.builder().userId(1L).group(group10).build(),
                GroupMember.builder().userId(1L).group(group20).build()));
    when(groupMemberRepository.findByUserId(2L))
        .thenReturn(
            List.of(
                GroupMember.builder().userId(2L).group(group10).build(),
                GroupMember.builder().userId(2L).group(group30).build()));

    when(balanceService.calculateUserBalanceInGroup(1L, 10L)).thenReturn(new BigDecimal("-30.00"));
    when(balanceService.calculateUserBalanceInGroup(1L, 20L)).thenReturn(new BigDecimal("-20.00"));
    when(balanceService.calculateUserBalanceInGroup(2L, 10L)).thenReturn(new BigDecimal("30.00"));
    when(balanceService.calculateUserBalanceInGroup(2L, 30L)).thenReturn(new BigDecimal("20.00"));

    NetBalanceResult result = source.resolve(1L, List.of(1L, 2L));

    Map<Long, BigDecimal> expected =
        Map.of(1L, new BigDecimal("-50.00"), 2L, new BigDecimal("50.00"));
    assertThat(result.getNetBalances()).isEqualTo(expected);
    assertThat(result.getOriginalTransactionCount()).isZero();
    verify(balanceService).calculateUserBalanceInGroup(1L, 10L);
    verify(balanceService).calculateUserBalanceInGroup(1L, 20L);
    verify(balanceService).calculateUserBalanceInGroup(2L, 10L);
    verify(balanceService).calculateUserBalanceInGroup(2L, 30L);
  }

  @Test
  @DisplayName("Should resolve a member with no memberships to a zero balance")
  void shouldResolveZeroBalanceForMemberWithoutMemberships() {
    when(groupMemberRepository.findByUserId(9L)).thenReturn(List.of());

    NetBalanceResult result = source.resolve(1L, List.of(9L));

    assertThat(result.getNetBalances().get(9L)).isEqualByComparingTo("0.00");
  }
}
