package com.splitz.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.balancesource.CrossGroupNetBalanceSource;
import com.splitz.expense.balancesource.IntraGroupNetBalanceSource;
import com.splitz.expense.balancesource.NetBalanceResult;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.DebtSimplificationPlanDTO;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.model.DebtSimplificationPlan;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupSimplificationSettings;
import com.splitz.expense.model.PlanStatus;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.model.SimplifiedDebtTransaction;
import com.splitz.expense.model.TransactionStatus;
import com.splitz.expense.netting.DebtNettingEngine;
import com.splitz.expense.netting.DebtProjectionEngine;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.LedgerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DebtSimplificationPlanServiceTest {

  @Mock private GroupRepository groupRepository;
  @Mock private GroupMemberRepository groupMemberRepository;
  @Mock private GroupSimplificationSettingsService settingsService;
  @Mock private UserSimplificationPreferenceService preferenceService;
  @Mock private UserClient userClient;
  @Mock private DebtNettingEngine debtNettingEngine;
  @Mock private LedgerRepository ledgerRepository;
  @Mock private IntraGroupNetBalanceSource intraGroupNetBalanceSource;
  @Mock private CrossGroupNetBalanceSource crossGroupNetBalanceSource;

  private DebtSimplificationPlanService planService;

  private static final Long GROUP_ID = 10L;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    planService =
        new DebtSimplificationPlanService(
            groupRepository,
            groupMemberRepository,
            settingsService,
            preferenceService,
            userClient,
            new DebtProjectionEngine(preferenceService, ledgerRepository, debtNettingEngine),
            intraGroupNetBalanceSource,
            crossGroupNetBalanceSource);
    when(groupRepository.existsById(GROUP_ID)).thenReturn(true);
    // Default: no member holds an account-level override, so the effective set is the group's own.
    // Individual tests override this to exercise the hard-override invariant.
    when(preferenceService.effectiveOptOutUserIds(anySet(), any()))
        .thenAnswer(invocation -> new HashSet<>(invocation.getArgument(0)));
  }

  private GroupMember member(Long userId) {
    return GroupMember.builder().userId(userId).group(Group.builder().id(GROUP_ID).build()).build();
  }

  private GroupSimplificationSettings enabledSettings(
      SimplificationScope scope, Set<Long> optOuts) {
    return GroupSimplificationSettings.builder()
        .groupId(GROUP_ID)
        .simplificationEnabled(true)
        .simplificationScope(scope)
        .optOutUserIds(optOuts)
        .build();
  }

  @Test
  @DisplayName("computePlan resolves a source for the scope and nets the source's balances")
  void computePlan_delegatesToSourceForScopeAndNets() {
    Set<Long> optOuts = new HashSet<>();
    optOuts.add(3L);
    when(settingsService.readSettings(GROUP_ID))
        .thenReturn(enabledSettings(SimplificationScope.INTRA_GROUP, optOuts));

    List<GroupMember> members = List.of(member(1L), member(2L), member(3L));
    when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(members);

    Map<Long, BigDecimal> netBalances = new HashMap<>();
    netBalances.put(1L, new BigDecimal("-50.00"));
    netBalances.put(2L, new BigDecimal("50.00"));
    when(intraGroupNetBalanceSource.resolve(eq(GROUP_ID), any()))
        .thenReturn(
            NetBalanceResult.builder()
                .netBalances(netBalances)
                .originalTransactionCount(7)
                .build());

    when(userClient.getUsersByIds(any()))
        .thenReturn(
            List.of(
                UserResponse.builder().id(1L).username("alice").build(),
                UserResponse.builder().id(2L).username("bob").build()));

    SimplifiedDebtTransaction tx =
        SimplifiedDebtTransaction.builder()
            .fromUserId(1L)
            .fromUsername("alice")
            .toUserId(2L)
            .toUsername("bob")
            .amount(new BigDecimal("50.00"))
            .status(TransactionStatus.PENDING)
            .build();
    when(debtNettingEngine.simplifyDebts(
            eq(GROUP_ID), eq(netBalances), eq(optOuts), any(), anyInt()))
        .thenReturn(plan(1, List.of(tx)));

    DebtSimplificationPlanDTO result = planService.computePlan(GROUP_ID);

    assertThat(result.getGroupId()).isEqualTo(GROUP_ID);
    assertThat(result.getScope()).isEqualTo("INTRA_GROUP");
    assertThat(result.getOptedOutUserIds()).contains(3L);
    assertThat(result.getTransactions()).hasSize(1);
    assertThat(result.getTransactions().get(0).getFromUserId()).isEqualTo(1L);
    verify(intraGroupNetBalanceSource).resolve(eq(GROUP_ID), any());
    verify(debtNettingEngine)
        .simplifyDebts(eq(GROUP_ID), eq(netBalances), eq(optOuts), any(), eq(7));
  }

  @Test
  @DisplayName("computePlan picks the cross-group source when scope is CROSS_GROUP")
  void computePlan_crossGroupPicker() {
    when(settingsService.readSettings(GROUP_ID))
        .thenReturn(enabledSettings(SimplificationScope.CROSS_GROUP, new HashSet<>()));

    when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(member(1L), member(2L)));

    Map<Long, BigDecimal> netBalances = new HashMap<>();
    netBalances.put(1L, new BigDecimal("-50.00"));
    netBalances.put(2L, new BigDecimal("50.00"));
    when(crossGroupNetBalanceSource.resolve(eq(GROUP_ID), any()))
        .thenReturn(
            NetBalanceResult.builder()
                .netBalances(netBalances)
                .originalTransactionCount(0)
                .build());

    when(userClient.getUsersByIds(any())).thenReturn(Collections.emptyList());
    when(debtNettingEngine.simplifyDebts(anyLong(), any(), any(), any(), anyInt()))
        .thenReturn(plan(0, Collections.emptyList()));

    DebtSimplificationPlanDTO result = planService.computePlan(GROUP_ID);

    assertThat(result.getScope()).isEqualTo("CROSS_GROUP");
    verify(crossGroupNetBalanceSource).resolve(eq(GROUP_ID), any());
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
    when(settingsService.readSettings(GROUP_ID)).thenReturn(disabled);

    DebtSimplificationPlanDTO result = planService.computePlan(GROUP_ID);

    assertThat(result.isSimplificationEnabled()).isFalse();
    assertThat(result.getSimplifiedTransactionCount()).isZero();
    assertThat(result.getTransactions()).isEmpty();
    verify(debtNettingEngine, never()).simplifyDebts(anyLong(), any(), any(), any(), anyInt());
    verify(intraGroupNetBalanceSource, never()).resolve(any(), any());
  }

  @Test
  @DisplayName(
      "computePlan nets with the account-level override even when the group opted nobody out")
  void computePlan_accountOverride_appliesAsHardOverride() {
    when(settingsService.readSettings(GROUP_ID))
        .thenReturn(enabledSettings(SimplificationScope.INTRA_GROUP, new HashSet<>()));
    when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(member(1L), member(2L)));

    // Bob opted out account-wide after this group was already configured.
    when(preferenceService.effectiveOptOutUserIds(anySet(), any())).thenReturn(Set.of(2L));

    Map<Long, BigDecimal> netBalances = new HashMap<>();
    netBalances.put(1L, new BigDecimal("-50.00"));
    netBalances.put(2L, new BigDecimal("50.00"));
    when(intraGroupNetBalanceSource.resolve(eq(GROUP_ID), any()))
        .thenReturn(
            NetBalanceResult.builder()
                .netBalances(netBalances)
                .originalTransactionCount(2)
                .build());
    when(userClient.getUsersByIds(any()))
        .thenReturn(
            List.of(
                UserResponse.builder().id(1L).username("alice").build(),
                UserResponse.builder().id(2L).username("bob").build()));
    when(debtNettingEngine.simplifyDebts(
            eq(GROUP_ID), eq(netBalances), eq(Set.of(2L)), any(), anyInt()))
        .thenReturn(plan(0, Collections.emptyList()));

    DebtSimplificationPlanDTO result = planService.computePlan(GROUP_ID);

    // The netting engine must be handed the overridden set, not the group's empty one.
    verify(debtNettingEngine)
        .simplifyDebts(eq(GROUP_ID), eq(netBalances), eq(Set.of(2L)), any(), eq(2));
    assertThat(result.getOptedOutUserIds()).containsExactly(2L);
  }

  @Test
  @DisplayName("computePlan reports the account override even when the group has netting disabled")
  void computePlan_accountOverride_reportedOnDisabledPlan() {
    GroupSimplificationSettings disabled =
        GroupSimplificationSettings.builder()
            .groupId(GROUP_ID)
            .simplificationEnabled(false)
            .simplificationScope(SimplificationScope.INTRA_GROUP)
            .optOutUserIds(new HashSet<>())
            .build();
    when(settingsService.readSettings(GROUP_ID)).thenReturn(disabled);
    when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(member(1L), member(2L)));
    when(preferenceService.effectiveOptOutUserIds(anySet(), any())).thenReturn(Set.of(2L));

    DebtSimplificationPlanDTO result = planService.computePlan(GROUP_ID);

    // optedOutUserIds means the same thing whether or not netting is on.
    assertThat(result.getOptedOutUserIds()).containsExactly(2L);
  }

  @Test
  @DisplayName("computePlan reads the default settings through the settings service")
  void computePlan_readsSettingsFromSettingsService() {
    when(settingsService.readSettings(GROUP_ID))
        .thenReturn(enabledSettings(SimplificationScope.INTRA_GROUP, new HashSet<>()));
    when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(member(1L), member(2L)));

    Map<Long, BigDecimal> zero = new HashMap<>();
    zero.put(1L, BigDecimal.ZERO);
    zero.put(2L, BigDecimal.ZERO);
    when(intraGroupNetBalanceSource.resolve(eq(GROUP_ID), any()))
        .thenReturn(
            NetBalanceResult.builder().netBalances(zero).originalTransactionCount(0).build());
    when(userClient.getUsersByIds(any())).thenReturn(Collections.emptyList());
    when(debtNettingEngine.simplifyDebts(anyLong(), any(), any(), any(), anyInt()))
        .thenReturn(plan(0, Collections.emptyList()));

    DebtSimplificationPlanDTO result = planService.computePlan(GROUP_ID);

    assertThat(result.isSimplificationEnabled()).isTrue();
    assertThat(result.getScope()).isEqualTo("INTRA_GROUP");
    verify(settingsService).readSettings(GROUP_ID);
  }

  @Test
  @DisplayName("computePlan throws when group does not exist")
  void computePlan_groupNotFound_throws() {
    when(groupRepository.existsById(999L)).thenReturn(false);

    assertThatThrownBy(() -> planService.computePlan(999L))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(debtNettingEngine, never()).simplifyDebts(anyLong(), any(), any(), any(), anyInt());
  }

  private static DebtSimplificationPlan plan(
      int originalCount, List<SimplifiedDebtTransaction> txs) {
    DebtSimplificationPlan plan =
        DebtSimplificationPlan.builder()
            .groupId(GROUP_ID)
            .status(PlanStatus.PROPOSED)
            .originalTransactionCount(originalCount)
            .optedOutUserIds(new HashSet<>())
            .transactions(new ArrayList<>())
            .totalDebtVolume(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .build();
    plan.setTransactions(txs);
    plan.setSimplifiedTransactionCount(txs.size());
    return plan;
  }
}
