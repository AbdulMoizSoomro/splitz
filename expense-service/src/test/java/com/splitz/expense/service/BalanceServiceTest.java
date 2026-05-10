package com.splitz.expense.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.BalanceDTO;
import com.splitz.expense.dto.DebtDTO;
import com.splitz.expense.dto.FriendBalanceResponseDTO;
import com.splitz.expense.dto.GroupBalanceResponseDTO;
import com.splitz.expense.dto.UserBalanceResponseDTO;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.FriendshipSettlement;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.Settlement;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.FriendshipSettlementRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.SettlementRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

  @Mock private ExpenseRepository expenseRepository;
  @Mock private GroupMemberRepository groupMemberRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private FriendshipSettlementRepository friendshipSettlementRepository;
  @Mock private UserClient userClient;

  @InjectMocks private BalanceService balanceService;

  private Group group;

  @BeforeEach
  void setUp() {
    group = Group.builder().id(1L).name("Test Group").build();
  }

  @Test
  void getGroupBalances_Success() {
    // Arrange
    GroupMember m1 = GroupMember.builder().userId(1L).group(group).build();
    GroupMember m2 = GroupMember.builder().userId(2L).group(group).build();
    when(groupRepository.existsById(1L)).thenReturn(true);
    when(groupMemberRepository.findByGroupId(1L)).thenReturn(Arrays.asList(m1, m2));

    Expense e1 = Expense.builder().paidBy(1L).amount(new BigDecimal("100.00")).build();
    e1.setSplits(
        Arrays.asList(
            ExpenseSplit.builder().userId(1L).shareAmount(new BigDecimal("50.00")).build(),
            ExpenseSplit.builder().userId(2L).shareAmount(new BigDecimal("50.00")).build()));
    when(expenseRepository.findByGroupId(1L)).thenReturn(Collections.singletonList(e1));

    when(settlementRepository.findByGroupId(1L)).thenReturn(Collections.emptyList());

    UserResponse u1 = UserResponse.builder().id(1L).username("user1").build();
    UserResponse u2 = UserResponse.builder().id(2L).username("user2").build();
    when(userClient.getUsersByIds(anyList())).thenReturn(Arrays.asList(u1, u2));

    // Act
    GroupBalanceResponseDTO response = balanceService.getGroupBalances(1L);

    // Assert
    assertEquals(1L, response.getGroupId());
    assertEquals(2, response.getBalances().size());

    BalanceDTO b1 = findBalance(response.getBalances(), 1L);
    BalanceDTO b2 = findBalance(response.getBalances(), 2L);

    assertEquals(0, new BigDecimal("50.00").compareTo(b1.getBalance())); // Paid 100, owes 50
    assertEquals(0, new BigDecimal("-50.00").compareTo(b2.getBalance())); // Paid 0, owes 50

    assertEquals(1, response.getSimplifiedDebts().size());
    DebtDTO debt = response.getSimplifiedDebts().get(0);
    assertEquals(2L, debt.getFrom());
    assertEquals(1L, debt.getTo());
    assertEquals(0, new BigDecimal("50.00").compareTo(debt.getAmount()));
  }

  @Test
  void getGroupBalances_WithSettlements() {
    GroupMember m1 = GroupMember.builder().userId(1L).group(group).build();
    GroupMember m2 = GroupMember.builder().userId(2L).group(group).build();
    when(groupRepository.existsById(1L)).thenReturn(true);
    when(groupMemberRepository.findByGroupId(1L)).thenReturn(Arrays.asList(m1, m2));

    Expense e1 = Expense.builder().paidBy(1L).amount(new BigDecimal("100.00")).build();
    e1.setSplits(
        Arrays.asList(
            ExpenseSplit.builder().userId(1L).shareAmount(new BigDecimal("50.00")).build(),
            ExpenseSplit.builder().userId(2L).shareAmount(new BigDecimal("50.00")).build()));
    when(expenseRepository.findByGroupId(1L)).thenReturn(Collections.singletonList(e1));

    // User 2 settles 30.00 to User 1
    Settlement s1 =
        Settlement.builder()
            .payerId(2L)
            .payeeId(1L)
            .amount(new BigDecimal("30.00"))
            .status(SettlementStatus.COMPLETED)
            .build();
    when(settlementRepository.findByGroupId(1L)).thenReturn(Collections.singletonList(s1));

    UserResponse u1 = UserResponse.builder().id(1L).username("user1").build();
    UserResponse u2 = UserResponse.builder().id(2L).username("user2").build();
    when(userClient.getUsersByIds(anyList())).thenReturn(Arrays.asList(u1, u2));

    GroupBalanceResponseDTO response = balanceService.getGroupBalances(1L);

    BalanceDTO b1 = findBalance(response.getBalances(), 1L);
    BalanceDTO b2 = findBalance(response.getBalances(), 2L);

    // Initial: 1: +50, 2: -50. After 30 settlement: 1: +50 - 30 = +20, 2: -50 + 30 = -20
    // Wait, the logic in service:
    // balances.put(payerId, balances.get(payerId).add(amount)); // Payer's balance increases (less
    // debt/more credit)
    // balances.put(payeeId, balances.get(payeeId).subtract(amount)); // Payee's balance decreases
    // (less credit)
    // 2 is payer, 1 is payee.
    // 2: -50 + 30 = -20.
    // 1: +50 - 30 = +20.
    assertEquals(0, new BigDecimal("20.00").compareTo(b1.getBalance()));
    assertEquals(0, new BigDecimal("-20.00").compareTo(b2.getBalance()));
  }

  @Test
  void getUserBalances_Success() {
    Group g1 = Group.builder().id(1L).name("Group 1").build();
    Group g2 = Group.builder().id(2L).name("Group 2").build();
    GroupMember m1 = GroupMember.builder().userId(101L).group(g1).build();
    GroupMember m2 = GroupMember.builder().userId(101L).group(g2).build();

    when(groupMemberRepository.findByUserId(101L)).thenReturn(Arrays.asList(m1, m2));
    when(groupRepository.existsById(1L)).thenReturn(true);
    when(groupRepository.existsById(2L)).thenReturn(true);

    // Group 1: User has +25.00 balance
    when(groupMemberRepository.findByGroupId(1L)).thenReturn(Collections.singletonList(m1));
    Expense e1 = Expense.builder().paidBy(101L).amount(new BigDecimal("50.00")).build();
    e1.setSplits(
        Arrays.asList(
            ExpenseSplit.builder().userId(101L).shareAmount(new BigDecimal("25.00")).build(),
            ExpenseSplit.builder().userId(102L).shareAmount(new BigDecimal("25.00")).build()));
    when(expenseRepository.findByGroupId(1L)).thenReturn(Collections.singletonList(e1));
    when(settlementRepository.findByGroupId(1L)).thenReturn(Collections.emptyList());

    // Group 2: User has -10.00 balance
    when(groupMemberRepository.findByGroupId(2L)).thenReturn(Collections.singletonList(m2));
    Expense e2 = Expense.builder().paidBy(102L).amount(new BigDecimal("20.00")).build();
    e2.setSplits(
        Arrays.asList(
            ExpenseSplit.builder().userId(101L).shareAmount(new BigDecimal("10.00")).build(),
            ExpenseSplit.builder().userId(102L).shareAmount(new BigDecimal("10.00")).build()));
    when(expenseRepository.findByGroupId(2L)).thenReturn(Collections.singletonList(e2));
    when(settlementRepository.findByGroupId(2L)).thenReturn(Collections.emptyList());

    when(userClient.getUsersByIds(anyList())).thenReturn(Collections.emptyList());
    when(userClient.getUserById(101L))
        .thenReturn(Optional.of(UserResponse.builder().id(101L).username("testuser").build()));

    when(friendshipSettlementRepository.findByPayerIdOrPayeeId(101L, 101L))
        .thenReturn(Collections.emptyList());

    UserBalanceResponseDTO response = balanceService.getUserBalances(101L);

    assertEquals(101L, response.getUserId());
    assertEquals(0, new BigDecimal("15.00").compareTo(response.getTotalBalance()));
    assertEquals(2, response.getGroupBalances().size());

    UserBalanceResponseDTO.GroupBalanceDTO gb1 = findGroupBalance(response.getGroupBalances(), 1L);
    UserBalanceResponseDTO.GroupBalanceDTO gb2 = findGroupBalance(response.getGroupBalances(), 2L);

    assertEquals(0, new BigDecimal("25.00").compareTo(gb1.getBalance()));
    assertEquals(0, new BigDecimal("-10.00").compareTo(gb2.getBalance()));
  }

  @Test
  void getUserBalances_WithGlobalSettlements() {
    when(groupMemberRepository.findByUserId(101L)).thenReturn(Collections.emptyList());

    // Global Settlement: User 101 pays 50.00
    FriendshipSettlement settlement =
        FriendshipSettlement.builder()
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .status(SettlementStatus.COMPLETED)
            .build();
    when(friendshipSettlementRepository.findByPayerIdOrPayeeId(101L, 101L))
        .thenReturn(Collections.singletonList(settlement));
    when(userClient.getUserById(101L))
        .thenReturn(Optional.of(UserResponse.builder().id(101L).build()));

    UserBalanceResponseDTO response = balanceService.getUserBalances(101L);

    assertEquals(0, new BigDecimal("50.00").compareTo(response.getTotalBalance()));
  }

  @Test
  void getNetBalanceWithFriend_CombinesGroupAndGlobal() {
    // Shared Group 1
    Group group1 = Group.builder().id(1L).build();
    GroupMember m1 = GroupMember.builder().group(group1).userId(101L).build();
    GroupMember m2 = GroupMember.builder().group(group1).userId(102L).build();

    when(groupMemberRepository.findByUserId(101L)).thenReturn(Collections.singletonList(m1));
    when(groupMemberRepository.findByUserId(102L)).thenReturn(Collections.singletonList(m2));

    // Mocks for calculateTotalOwedBetweenUsers
    when(expenseRepository.calculateTotalOwedBetweenUsers(eq(101L), eq(102L), anySet()))
        .thenReturn(new BigDecimal("15.00"));
    when(expenseRepository.calculateTotalOwedBetweenUsers(eq(102L), eq(101L), anySet()))
        .thenReturn(BigDecimal.ZERO);

    // Mocks for calculateTotalSettledBetweenUsers (Group)
    when(settlementRepository.calculateTotalSettledBetweenUsers(
            eq(101L), eq(102L), anySet(), any()))
        .thenReturn(BigDecimal.ZERO);
    when(settlementRepository.calculateTotalSettledBetweenUsers(
            eq(102L), eq(101L), anySet(), any()))
        .thenReturn(BigDecimal.ZERO);

    // Mocks for calculateTotalSettledBetweenUsers (Global)
    when(friendshipSettlementRepository.calculateTotalSettledBetweenUsers(
            eq(101L), eq(102L), any()))
        .thenReturn(BigDecimal.ZERO);
    when(friendshipSettlementRepository.calculateTotalSettledBetweenUsers(
            eq(102L), eq(101L), any()))
        .thenReturn(new BigDecimal("10.00"));

    FriendBalanceResponseDTO result = balanceService.getNetBalanceWithFriend(101L, 102L);

    // Expected: 15 (owed from expense) - 10 (settled globally) = 5
    assertEquals(0, new BigDecimal("5.00").compareTo(result.getNetBalance()));
  }

  private BalanceDTO findBalance(List<BalanceDTO> balances, Long userId) {
    return balances.stream().filter(b -> b.getUserId().equals(userId)).findFirst().orElseThrow();
  }

  private UserBalanceResponseDTO.GroupBalanceDTO findGroupBalance(
      List<UserBalanceResponseDTO.GroupBalanceDTO> balances, Long groupId) {
    return balances.stream().filter(b -> b.getGroupId().equals(groupId)).findFirst().orElseThrow();
  }
}
