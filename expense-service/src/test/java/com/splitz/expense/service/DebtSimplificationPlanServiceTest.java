package com.splitz.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.balance.DebtBalanceEngine;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.DebtSimplificationPlanDTO;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.model.DebtSimplificationPlan;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupSimplificationSettings;
import com.splitz.expense.model.PlanStatus;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.model.SimplifiedDebtTransaction;
import com.splitz.expense.model.TransactionStatus;
import com.splitz.expense.netting.DebtNettingEngine;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.GroupSimplificationSettingsRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DebtSimplificationPlanServiceTest {

  @Mock private GroupRepository groupRepository;
  @Mock private GroupMemberRepository groupMemberRepository;
  @Mock private ExpenseRepository expenseRepository;
  @Mock private SettlementAllocationRepository settlementAllocationRepository;
  @Mock private GroupSimplificationSettingsRepository settingsRepository;
  @Mock private UserClient userClient;
  @Mock private DebtBalanceEngine debtBalanceEngine;
  @Mock private DebtNettingEngine debtNettingEngine;
  @Mock private BalanceService balanceService;

  private DebtSimplificationPlanService planService;

  private static final Long GROUP_ID = 10L;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    planService =
        new DebtSimplificationPlanService(
            groupRepository,
            groupMemberRepository,
            expenseRepository,
            settlementAllocationRepository,
            settingsRepository,
            userClient,
            debtBalanceEngine,
            debtNettingEngine,
            balanceService);
    when(groupRepository.existsById(GROUP_ID)).thenReturn(true);
  }

  private GroupMember member(Long userId) {
    return GroupMember.builder().userId(userId).group(Group.builder().id(GROUP_ID).build()).build();
  }

  private GroupSimplificationSettings enabledSettings(
      SimplificationScope scope, Set<Long> optOuts) {
    return GroupSimplificationSettings.builder()
        .id(1L)
        .groupId(GROUP_ID)
        .simplificationEnabled(true)
        .simplificationScope(scope)
        .optOutUserIds(optOuts)
        .build();
  }

  private DebtSimplificationPlan aPlan(int originalCount, List<SimplifiedDebtTransaction> txs) {
    DebtSimplificationPlan plan =
        DebtSimplificationPlan.builder()
            .groupId(GROUP_ID)
            .status(PlanStatus.PROPOSED)
            .originalTransactionCount(originalCount)
            .optedOutUserIds(new HashSet<>())
            .transactions(new java.util.ArrayList<>())
            .totalDebtVolume(BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP))
            .build();
    plan.setTransactions(txs);
    plan.setSimplifiedTransactionCount(txs.size());
    return plan;
  }

  @Test
  @DisplayName("computePlan intra-group returns simplified plan with opt-outs honoured")
  void computePlan_intraGroup_returnsSimplifiedPlan() {
    Set<Long> optOuts = new HashSet<>();
    optOuts.add(3L);
    when(settingsRepository.findByGroupId(GROUP_ID))
        .thenReturn(Optional.of(enabledSettings(SimplificationScope.INTRA_GROUP, optOuts)));

    List<GroupMember> members = List.of(member(1L), member(2L), member(3L));
    when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(members);

    List<Expense> expenses =
        List.of(Expense.builder().paidBy(2L).amount(new BigDecimal("100.00")).build());
    when(expenseRepository.findByGroupId(GROUP_ID)).thenReturn(expenses);
    when(settlementAllocationRepository.findByGroupId(GROUP_ID))
        .thenReturn(Collections.emptyList());

    Map<Long, BigDecimal> netBalances = new HashMap<>();
    netBalances.put(1L, new BigDecimal("-50.00"));
    netBalances.put(2L, new BigDecimal("0.00"));
    netBalances.put(3L, new BigDecimal("50.00"));
    when(debtBalanceEngine.calculateGroupBalances(
            eq(List.of(1L, 2L, 3L)), eq(expenses), eq(Collections.emptyList())))
        .thenReturn(netBalances);

    when(userClient.getUsersByIds(any()))
        .thenReturn(
            List.of(
                UserResponse.builder().id(1L).username("alice").build(),
                UserResponse.builder().id(2L).username("bob").build(),
                UserResponse.builder().id(3L).username("charlie").build()));

    SimplifiedDebtTransaction tx =
        SimplifiedDebtTransaction.builder()
            .fromUserId(1L)
            .fromUsername("alice")
            .toUserId(3L)
            .toUsername("charlie")
            .amount(new BigDecimal("50.00"))
            .status(TransactionStatus.PENDING)
            .build();
    when(debtNettingEngine.simplifyDebts(
            eq(GROUP_ID), eq(netBalances), eq(optOuts), any(), anyInt()))
        .thenReturn(aPlan(1, List.of(tx)));

    DebtSimplificationPlanDTO result = planService.computePlan(GROUP_ID);

    assertThat(result.getGroupId()).isEqualTo(GROUP_ID);
    assertThat(result.isSimplificationEnabled()).isTrue();
    assertThat(result.getScope()).isEqualTo("INTRA_GROUP");
    assertThat(result.getStatus()).isEqualTo("PROPOSED");
    assertThat(result.getSimplifiedTransactionCount()).isEqualTo(1);
    assertThat(result.getOptedOutUserIds()).contains(3L);
    assertThat(result.getTransactions()).hasSize(1);
    assertThat(result.getTransactions().get(0).getFromUserId()).isEqualTo(1L);
    assertThat(result.getTransactions().get(0).getToUsername()).isEqualTo("charlie");
    verify(debtBalanceEngine).calculateGroupBalances(any(), any(), any());
    verify(debtNettingEngine).simplifyDebts(eq(GROUP_ID), any(), eq(optOuts), any(), anyInt());
  }

  @Test
  @DisplayName("computePlan cross-group aggregates member balances across all their groups")
  void computePlan_crossGroup_aggregatesAcrossGroups() {
    when(settingsRepository.findByGroupId(GROUP_ID))
        .thenReturn(Optional.of(enabledSettings(SimplificationScope.CROSS_GROUP, new HashSet<>())));

    List<GroupMember> members = List.of(member(1L), member(2L));
    when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(members);

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

    when(userClient.getUsersByIds(any()))
        .thenReturn(
            List.of(
                UserResponse.builder().id(1L).username("alice").build(),
                UserResponse.builder().id(2L).username("bob").build()));

    Map<Long, BigDecimal> expectedNet = new HashMap<>();
    expectedNet.put(1L, new BigDecimal("-50.00"));
    expectedNet.put(2L, new BigDecimal("50.00"));
    when(debtNettingEngine.simplifyDebts(
            eq(GROUP_ID), eq(expectedNet), eq(new HashSet<>()), any(), anyInt()))
        .thenReturn(
            aPlan(
                0,
                List.of(
                    SimplifiedDebtTransaction.builder()
                        .fromUserId(1L)
                        .fromUsername("alice")
                        .toUserId(2L)
                        .toUsername("bob")
                        .amount(new BigDecimal("50.00"))
                        .status(TransactionStatus.PENDING)
                        .build())));

    DebtSimplificationPlanDTO result = planService.computePlan(GROUP_ID);

    assertThat(result.getScope()).isEqualTo("CROSS_GROUP");
    assertThat(result.getSimplifiedTransactionCount()).isEqualTo(1);
    assertThat(result.getTransactions().get(0).getAmount()).isEqualByComparingTo("50.00");
    verify(balanceService).calculateUserBalanceInGroup(1L, 10L);
    verify(balanceService).calculateUserBalanceInGroup(1L, 20L);
    verify(balanceService).calculateUserBalanceInGroup(2L, 10L);
    verify(balanceService).calculateUserBalanceInGroup(2L, 30L);
    verify(debtBalanceEngine, never()).calculateGroupBalances(any(), any(), any());
  }

  @Test
  @DisplayName("computePlan returns empty plan when simplification is disabled")
  void computePlan_disabled_returnsEmptyPlan() {
    GroupSimplificationSettings disabled =
        GroupSimplificationSettings.builder()
            .groupId(GROUP_ID)
            .simplificationEnabled(false)
            .simplificationScope(SimplificationScope.INTRA_GROUP)
            .optOutUserIds(new HashSet<>())
            .build();
    when(settingsRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(disabled));

    DebtSimplificationPlanDTO result = planService.computePlan(GROUP_ID);

    assertThat(result).isNotNull();
    assertThat(result.isSimplificationEnabled()).isFalse();
    assertThat(result.getSimplifiedTransactionCount()).isZero();
    assertThat(result.getTransactions()).isEmpty();
    verify(debtNettingEngine, never()).simplifyDebts(anyLong(), any(), any(), any(), anyInt());
  }

  @Test
  @DisplayName("computePlan uses default enabled INTRA_GROUP settings when none exist")
  void computePlan_noSettings_defaultsToEnabledIntraGroup() {
    when(settingsRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());
    when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(member(1L), member(2L)));
    when(expenseRepository.findByGroupId(GROUP_ID)).thenReturn(Collections.emptyList());
    when(settlementAllocationRepository.findByGroupId(GROUP_ID))
        .thenReturn(Collections.emptyList());

    Map<Long, BigDecimal> zeroBalances = new HashMap<>();
    zeroBalances.put(1L, BigDecimal.ZERO);
    zeroBalances.put(2L, BigDecimal.ZERO);
    when(debtBalanceEngine.calculateGroupBalances(any(), any(), any())).thenReturn(zeroBalances);
    when(userClient.getUsersByIds(any())).thenReturn(Collections.emptyList());
    when(debtNettingEngine.simplifyDebts(anyLong(), any(), any(), any(), anyInt()))
        .thenReturn(aPlan(0, Collections.emptyList()));

    DebtSimplificationPlanDTO result = planService.computePlan(GROUP_ID);

    assertThat(result.isSimplificationEnabled()).isTrue();
    assertThat(result.getScope()).isEqualTo("INTRA_GROUP");
    assertThat(result.getOptedOutUserIds()).isEmpty();
  }

  @Test
  @DisplayName("computePlan throws when group does not exist")
  void computePlan_groupNotFound_throws() {
    when(groupRepository.existsById(999L)).thenReturn(false);

    assertThatThrownBy(() -> planService.computePlan(999L))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(debtNettingEngine, never()).simplifyDebts(anyLong(), any(), any(), any(), anyInt());
  }
}
