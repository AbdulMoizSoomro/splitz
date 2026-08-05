package com.splitz.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.balance.DebtBalanceEngine;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.FriendBalanceResponseDTO;
import com.splitz.expense.dto.GroupBalanceResponseDTO;
import com.splitz.expense.dto.UserBalanceResponseDTO;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.model.DebtSimplificationPlan;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SimplifiedDebtTransaction;
import com.splitz.expense.model.TransactionStatus;
import com.splitz.expense.netting.DebtNettingEngine;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class BalanceServiceTest {

  @Mock private ExpenseRepository expenseRepository;
  @Mock private GroupMemberRepository groupMemberRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private SettlementAllocationRepository settlementAllocationRepository;
  @Mock private UserClient userClient;
  @Mock private DebtBalanceEngine debtBalanceEngine;
  @Mock private DebtNettingEngine debtNettingEngine;

  private BalanceService balanceService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    balanceService =
        new BalanceService(
            expenseRepository,
            groupMemberRepository,
            groupRepository,
            settlementAllocationRepository,
            userClient,
            debtBalanceEngine,
            debtNettingEngine);
  }

  @Test
  void shouldDelegateGroupBalancesAndDebtSimplificationToEngine() {
    Long groupId = 10L;

    when(groupRepository.existsById(groupId)).thenReturn(true);

    Group group = Group.builder().id(groupId).name("Group 1").build();
    GroupMember member1 = GroupMember.builder().userId(1L).group(group).build();
    GroupMember member2 = GroupMember.builder().userId(2L).group(group).build();
    List<GroupMember> members = List.of(member1, member2);
    when(groupMemberRepository.findByGroupId(groupId)).thenReturn(members);

    List<Expense> expenses =
        List.of(Expense.builder().paidBy(1L).amount(new BigDecimal("10.00")).build());
    when(expenseRepository.findByGroupId(groupId)).thenReturn(expenses);

    List<SettlementAllocation> allocations = Collections.emptyList();
    when(settlementAllocationRepository.findByGroupId(groupId)).thenReturn(allocations);

    // Mock engine behavior
    Map<Long, BigDecimal> computedBalances =
        Map.of(
            1L, new BigDecimal("5.00"),
            2L, new BigDecimal("-5.00"));
    when(debtBalanceEngine.calculateGroupBalances(any(), any(), any()))
        .thenReturn(computedBalances);

    UserResponse user1 =
        UserResponse.builder().id(1L).username("user1").email("user1@example.com").build();
    UserResponse user2 =
        UserResponse.builder().id(2L).username("user2").email("user2@example.com").build();
    when(userClient.getUsersByIds(any())).thenReturn(List.of(user1, user2));

    SimplifiedDebtTransaction tx =
        SimplifiedDebtTransaction.builder()
            .fromUserId(2L)
            .fromUsername("user2")
            .toUserId(1L)
            .toUsername("user1")
            .amount(new BigDecimal("5.00"))
            .status(TransactionStatus.PENDING)
            .build();
    DebtSimplificationPlan plan =
        DebtSimplificationPlan.builder().groupId(groupId).transactions(List.of(tx)).build();
    when(debtNettingEngine.simplifyDebts(any(), any(), any(), any(), anyInt())).thenReturn(plan);

    GroupBalanceResponseDTO result = balanceService.getGroupBalances(groupId);

    assertThat(result).isNotNull();
    assertThat(result.getGroupId()).isEqualTo(groupId);
    assertThat(result.getSimplifiedDebts()).hasSize(1);
    assertThat(result.getBalances()).hasSize(2);

    verify(debtBalanceEngine).calculateGroupBalances(any(), any(), any());
    verify(debtNettingEngine).simplifyDebts(any(), any(), any(), any(), anyInt());
  }

  @Test
  void shouldDelegateNetFriendBalanceToEngine() {
    Long userId = 1L;
    Long friendId = 2L;

    Group group = Group.builder().id(10L).name("Shared Group").build();
    GroupMember memberUser = GroupMember.builder().userId(userId).group(group).build();
    GroupMember memberFriend = GroupMember.builder().userId(friendId).group(group).build();

    when(groupMemberRepository.findByUserId(userId)).thenReturn(List.of(memberUser));
    when(groupMemberRepository.findByUserId(friendId)).thenReturn(List.of(memberFriend));
    when(groupRepository.findAllById(any())).thenReturn(List.of(group));

    List<Expense> expenses =
        List.of(
            Expense.builder().group(group).paidBy(userId).amount(new BigDecimal("20.00")).build());
    when(expenseRepository.findByGroupIdIn(any())).thenReturn(expenses);

    List<SettlementAllocation> allocations = Collections.emptyList();
    when(settlementAllocationRepository.findByGroupIdIn(any())).thenReturn(allocations);

    when(debtBalanceEngine.calculateNetBalanceInGroup(userId, friendId, expenses, allocations))
        .thenReturn(new BigDecimal("10.00"));

    // Global friendship settlements
    when(debtBalanceEngine.calculateGlobalSettlementBalance(userId, friendId))
        .thenReturn(BigDecimal.ZERO);

    FriendBalanceResponseDTO result = balanceService.getNetBalanceWithFriend(userId, friendId);

    assertThat(result).isNotNull();
    assertThat(result.getNetBalance()).isEqualByComparingTo("10.00");
    assertThat(result.getGroupBalances()).hasSize(1);
    assertThat(result.getGroupBalances().get(0).getBalance()).isEqualByComparingTo("10.00");

    verify(debtBalanceEngine).calculateNetBalanceInGroup(userId, friendId, expenses, allocations);
  }

  @Test
  void shouldComputeUserBalancesWithoutInlineAuthCheck() {
    Long userId = 1L;

    when(groupMemberRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
    when(debtBalanceEngine.calculateUserGlobalSettlementBalance(userId))
        .thenReturn(BigDecimal.ZERO);
    when(userClient.getUserById(userId))
        .thenReturn(
            java.util.Optional.of(new UserResponse(userId, "user1", "u@e.com", "First", "Last")));

    UserBalanceResponseDTO result = balanceService.getUserBalances(userId);

    assertThat(result).isNotNull();
    assertThat(result.getUserId()).isEqualTo(userId);
    assertThat(result.getTotalBalance()).isEqualByComparingTo("0.00");
  }

  @Test
  void shouldMapUserBalancesFromSingleBatchEngineCallIncludingZeroBalanceGroups() {
    Long userId = 1L;

    Group group10 = Group.builder().id(10L).name("Group A").build();
    Group group20 = Group.builder().id(20L).name("Group B").build();
    when(groupMemberRepository.findByUserId(userId))
        .thenReturn(
            List.of(
                GroupMember.builder().userId(userId).group(group10).build(),
                GroupMember.builder().userId(userId).group(group20).build()));

    when(debtBalanceEngine.calculateBalancesInGroups(List.of(userId), List.of(10L, 20L)))
        .thenReturn(Map.of(userId, Map.of(10L, new BigDecimal("35.00"), 20L, BigDecimal.ZERO)));
    when(debtBalanceEngine.calculateUserGlobalSettlementBalance(userId))
        .thenReturn(BigDecimal.ZERO);
    when(userClient.getUserById(userId))
        .thenReturn(
            java.util.Optional.of(new UserResponse(userId, "user1", "u@e.com", "First", "Last")));

    UserBalanceResponseDTO result = balanceService.getUserBalances(userId);

    assertThat(result.getGroupBalances()).hasSize(2);
    assertThat(result.getGroupBalances().get(0).getGroupId()).isEqualTo(10L);
    assertThat(result.getGroupBalances().get(0).getBalance()).isEqualByComparingTo("35.00");
    assertThat(result.getGroupBalances().get(1).getGroupId()).isEqualTo(20L);
    assertThat(result.getGroupBalances().get(1).getBalance()).isEqualByComparingTo("0.00");
    assertThat(result.getTotalBalance()).isEqualByComparingTo("35.00");
    verify(debtBalanceEngine).calculateBalancesInGroups(List.of(userId), List.of(10L, 20L));
  }
}
