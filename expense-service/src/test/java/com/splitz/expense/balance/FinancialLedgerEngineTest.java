package com.splitz.expense.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.CounterpartyResponseDTO;
import com.splitz.expense.dto.FriendBalanceResponseDTO;
import com.splitz.expense.dto.GroupBalanceResponseDTO;
import com.splitz.expense.dto.UserBalanceResponseDTO;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.model.DebtSimplificationPlan;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupSimplificationSettings;
import com.splitz.expense.model.PlanStatus;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.model.SimplifiedDebtTransaction;
import com.splitz.expense.model.TransactionStatus;
import com.splitz.expense.netting.DebtNettingEngine;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.PaymentRepository;
import com.splitz.expense.repository.UserGroupAggregate;
import com.splitz.expense.service.GroupSimplificationSettingsService;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FinancialLedgerEngineTest {

  private ExpenseRepository expenseRepository;
  private GroupMemberRepository groupMemberRepository;
  private GroupRepository groupRepository;
  private PaymentRepository paymentRepository;
  private UserClient userClient;
  private DebtNettingEngine debtNettingEngine;
  private GroupSimplificationSettingsService settingsService;

  private FinancialLedgerEngine engine;

  @BeforeEach
  void setUp() {
    expenseRepository = mock(ExpenseRepository.class);
    groupMemberRepository = mock(GroupMemberRepository.class);
    groupRepository = mock(GroupRepository.class);
    paymentRepository = mock(PaymentRepository.class);
    userClient = mock(UserClient.class);
    debtNettingEngine = mock(DebtNettingEngine.class);
    settingsService = mock(GroupSimplificationSettingsService.class);
    when(settingsService.readSettings(anyLong()))
        .thenAnswer(inv -> GroupSimplificationSettings.defaults(inv.getArgument(0)));

    engine =
        new DefaultFinancialLedgerEngine(
            expenseRepository,
            groupMemberRepository,
            groupRepository,
            paymentRepository,
            userClient,
            debtNettingEngine,
            settingsService);
  }

  @Nested
  @DisplayName("getCounterparties")
  class CounterpartiesTests {

    @Test
    @DisplayName("aggregates counterparty across multiple groups and enriches with user details")
    void aggregatesCounterpartyAcrossGroups() {
      Long userId = 1L;
      Group tripGroup = Group.builder().id(10L).name("Trip").build();
      Group flatGroup = Group.builder().id(20L).name("Flat").build();

      GroupMember m1Trip = GroupMember.builder().id(101L).group(tripGroup).userId(userId).build();
      GroupMember m1Flat = GroupMember.builder().id(102L).group(flatGroup).userId(userId).build();

      when(groupMemberRepository.findByUserId(userId)).thenReturn(List.of(m1Trip, m1Flat));

      GroupMember m2Trip = GroupMember.builder().id(201L).group(tripGroup).userId(2L).build();
      GroupMember m2Flat = GroupMember.builder().id(202L).group(flatGroup).userId(2L).build();

      when(groupMemberRepository.findByGroupId(10L)).thenReturn(List.of(m1Trip, m2Trip));
      when(groupMemberRepository.findByGroupId(20L)).thenReturn(List.of(m1Flat, m2Flat));

      when(expenseRepository.findByGroupId(10L)).thenReturn(Collections.emptyList());
      when(expenseRepository.findByGroupId(20L)).thenReturn(Collections.emptyList());
      when(paymentRepository.findByGroupId(10L)).thenReturn(Collections.emptyList());
      when(paymentRepository.findByGroupId(20L)).thenReturn(Collections.emptyList());

      // In Trip: 2 owes 1 $15
      DebtSimplificationPlan tripPlan =
          DebtSimplificationPlan.builder()
              .groupId(10L)
              .status(PlanStatus.PROPOSED)
              .transactions(
                  List.of(
                      SimplifiedDebtTransaction.builder()
                          .fromUserId(2L)
                          .toUserId(1L)
                          .amount(new BigDecimal("15.00"))
                          .status(TransactionStatus.PENDING)
                          .build()))
              .build();

      // In Flat: 1 owes 2 $5
      DebtSimplificationPlan flatPlan =
          DebtSimplificationPlan.builder()
              .groupId(20L)
              .status(PlanStatus.PROPOSED)
              .transactions(
                  List.of(
                      SimplifiedDebtTransaction.builder()
                          .fromUserId(1L)
                          .toUserId(2L)
                          .amount(new BigDecimal("5.00"))
                          .status(TransactionStatus.PENDING)
                          .build()))
              .build();

      when(debtNettingEngine.simplifyDebts(
              eq(10L), anyMap(), anySet(), anyMap(), any(Integer.class)))
          .thenReturn(tripPlan);
      when(debtNettingEngine.simplifyDebts(
              eq(20L), anyMap(), anySet(), anyMap(), any(Integer.class)))
          .thenReturn(flatPlan);

      UserResponse bob =
          UserResponse.builder()
              .id(2L)
              .username("bob")
              .firstName("Bob")
              .lastName("Smith")
              .email("bob@example.com")
              .build();
      when(userClient.getUsersByIds(List.of(2L))).thenReturn(List.of(bob));

      List<CounterpartyResponseDTO> result = engine.getCounterparties(userId);

      assertThat(result).hasSize(1);
      CounterpartyResponseDTO cp = result.get(0);
      assertThat(cp.getUserId()).isEqualTo(2L);
      assertThat(cp.getUsername()).isEqualTo("bob");
      assertThat(cp.getFirstName()).isEqualTo("Bob");
      assertThat(cp.getLastName()).isEqualTo("Smith");
      assertThat(cp.getEmail()).isEqualTo("bob@example.com");
      // 15 (owed to 1) - 5 (1 owes 2) = +10.00 (bob owes user)
      assertThat(cp.getBalance()).isEqualByComparingTo(new BigDecimal("10.00"));
      assertThat(cp.getGroups())
          .extracting(CounterpartyResponseDTO.GroupRefDTO::getName)
          .containsExactlyInAnyOrder("Trip", "Flat");
    }

    @Test
    @DisplayName("filters out counterparties whose net balance cancels to zero")
    void filtersOutZeroBalanceCounterparties() {
      Long userId = 1L;
      Group tripGroup = Group.builder().id(10L).name("Trip").build();
      Group flatGroup = Group.builder().id(20L).name("Flat").build();

      GroupMember m1Trip = GroupMember.builder().id(101L).group(tripGroup).userId(userId).build();
      GroupMember m1Flat = GroupMember.builder().id(102L).group(flatGroup).userId(userId).build();

      when(groupMemberRepository.findByUserId(userId)).thenReturn(List.of(m1Trip, m1Flat));
      when(groupMemberRepository.findByGroupId(10L)).thenReturn(List.of(m1Trip));
      when(groupMemberRepository.findByGroupId(20L)).thenReturn(List.of(m1Flat));

      DebtSimplificationPlan tripPlan =
          DebtSimplificationPlan.builder()
              .groupId(10L)
              .transactions(
                  List.of(
                      SimplifiedDebtTransaction.builder()
                          .fromUserId(2L)
                          .toUserId(1L)
                          .amount(new BigDecimal("10.00"))
                          .build()))
              .build();

      DebtSimplificationPlan flatPlan =
          DebtSimplificationPlan.builder()
              .groupId(20L)
              .transactions(
                  List.of(
                      SimplifiedDebtTransaction.builder()
                          .fromUserId(1L)
                          .toUserId(2L)
                          .amount(new BigDecimal("10.00"))
                          .build()))
              .build();

      when(debtNettingEngine.simplifyDebts(
              eq(10L), anyMap(), anySet(), anyMap(), any(Integer.class)))
          .thenReturn(tripPlan);
      when(debtNettingEngine.simplifyDebts(
              eq(20L), anyMap(), anySet(), anyMap(), any(Integer.class)))
          .thenReturn(flatPlan);

      List<CounterpartyResponseDTO> result = engine.getCounterparties(userId);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("group and user balances")
  class GroupAndUserBalancesTests {

    @Test
    @DisplayName("getGroupBalances computes member balances and simplified debts")
    void computesGroupBalances() {
      Long groupId = 10L;
      when(groupRepository.existsById(groupId)).thenReturn(true);

      Group group = Group.builder().id(groupId).name("Group 1").build();
      GroupMember member1 = GroupMember.builder().userId(1L).group(group).build();
      GroupMember member2 = GroupMember.builder().userId(2L).group(group).build();
      when(groupMemberRepository.findByGroupId(groupId)).thenReturn(List.of(member1, member2));

      List<Expense> expenses =
          List.of(
              Expense.builder()
                  .paidBy(1L)
                  .amount(new BigDecimal("10.00"))
                  .splits(
                      List.of(
                          com.splitz.expense.model.ExpenseSplit.builder()
                              .userId(1L)
                              .shareAmount(new BigDecimal("5.00"))
                              .build(),
                          com.splitz.expense.model.ExpenseSplit.builder()
                              .userId(2L)
                              .shareAmount(new BigDecimal("5.00"))
                              .build()))
                  .build());
      when(expenseRepository.findByGroupId(groupId)).thenReturn(expenses);
      when(paymentRepository.findByGroupId(groupId)).thenReturn(Collections.emptyList());

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
      when(debtNettingEngine.simplifyDebts(
              eq(groupId), anyMap(), anySet(), anyMap(), any(Integer.class)))
          .thenReturn(plan);

      GroupBalanceResponseDTO result = engine.getGroupBalances(groupId);

      assertThat(result).isNotNull();
      assertThat(result.getGroupId()).isEqualTo(groupId);
      assertThat(result.getSimplifiedDebts()).hasSize(1);
      assertThat(result.getBalances()).hasSize(2);
    }

    @Test
    @DisplayName("getGroupBalances reverts to raw pairwise debts when simplification is disabled")
    void revertsToRawDebtsWhenSimplificationDisabled() {
      Long groupId = 10L;
      when(groupRepository.existsById(groupId)).thenReturn(true);
      when(settingsService.readSettings(groupId))
          .thenReturn(
              GroupSimplificationSettings.builder()
                  .groupId(groupId)
                  .simplificationEnabled(false)
                  .build());

      Group group = Group.builder().id(groupId).name("Group 1").build();
      GroupMember m1 = GroupMember.builder().userId(1L).group(group).build();
      GroupMember m2 = GroupMember.builder().userId(2L).group(group).build();
      GroupMember m3 = GroupMember.builder().userId(3L).group(group).build();
      when(groupMemberRepository.findByGroupId(groupId)).thenReturn(List.of(m1, m2, m3));

      // 1 paid 60 (20 each for 1, 2, 3)
      // 2 paid 30 (10 each for 1, 2, 3)
      Expense exp1 =
          Expense.builder()
              .paidBy(1L)
              .amount(new BigDecimal("60.00"))
              .splits(
                  List.of(
                      com.splitz.expense.model.ExpenseSplit.builder()
                          .userId(1L)
                          .shareAmount(new BigDecimal("20.00"))
                          .build(),
                      com.splitz.expense.model.ExpenseSplit.builder()
                          .userId(2L)
                          .shareAmount(new BigDecimal("20.00"))
                          .build(),
                      com.splitz.expense.model.ExpenseSplit.builder()
                          .userId(3L)
                          .shareAmount(new BigDecimal("20.00"))
                          .build()))
              .build();
      Expense exp2 =
          Expense.builder()
              .paidBy(2L)
              .amount(new BigDecimal("30.00"))
              .splits(
                  List.of(
                      com.splitz.expense.model.ExpenseSplit.builder()
                          .userId(1L)
                          .shareAmount(new BigDecimal("10.00"))
                          .build(),
                      com.splitz.expense.model.ExpenseSplit.builder()
                          .userId(2L)
                          .shareAmount(new BigDecimal("10.00"))
                          .build(),
                      com.splitz.expense.model.ExpenseSplit.builder()
                          .userId(3L)
                          .shareAmount(new BigDecimal("10.00"))
                          .build()))
              .build();
      when(expenseRepository.findByGroupId(groupId)).thenReturn(List.of(exp1, exp2));
      when(paymentRepository.findByGroupId(groupId)).thenReturn(Collections.emptyList());

      UserResponse u1 = UserResponse.builder().id(1L).username("alice").build();
      UserResponse u2 = UserResponse.builder().id(2L).username("bob").build();
      UserResponse u3 = UserResponse.builder().id(3L).username("charlie").build();
      when(userClient.getUsersByIds(any())).thenReturn(List.of(u1, u2, u3));

      GroupBalanceResponseDTO result = engine.getGroupBalances(groupId);

      assertThat(result).isNotNull();
      assertThat(result.getSimplifiedDebts()).hasSize(3);
      assertThat(result.getSimplifiedDebts())
          .anySatisfy(
              d -> {
                assertThat(d.getFrom()).isEqualTo(2L);
                assertThat(d.getTo()).isEqualTo(1L);
                assertThat(d.getAmount()).isEqualByComparingTo("10.00");
              });
      assertThat(result.getSimplifiedDebts())
          .anySatisfy(
              d -> {
                assertThat(d.getFrom()).isEqualTo(3L);
                assertThat(d.getTo()).isEqualTo(1L);
                assertThat(d.getAmount()).isEqualByComparingTo("20.00");
              });
      assertThat(result.getSimplifiedDebts())
          .anySatisfy(
              d -> {
                assertThat(d.getFrom()).isEqualTo(3L);
                assertThat(d.getTo()).isEqualTo(2L);
                assertThat(d.getAmount()).isEqualByComparingTo("10.00");
              });
    }

    @Test
    @DisplayName("getUserBalances computes total balance and group breakdown")
    void computesUserBalances() {
      Long userId = 1L;
      when(groupMemberRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
      when(paymentRepository.findByPayerIdOrPayeeId(userId, userId))
          .thenReturn(Collections.emptyList());
      when(userClient.getUserById(userId))
          .thenReturn(Optional.of(new UserResponse(userId, "user1", "u@e.com", "First", "Last")));

      UserBalanceResponseDTO result = engine.getUserBalances(userId);

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo(userId);
      assertThat(result.getTotalBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("getNetBalanceWithFriend calculates pairwise net balance across shared groups")
    void calculatesFriendBalance() {
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
              Expense.builder()
                  .group(group)
                  .paidBy(userId)
                  .amount(new BigDecimal("20.00"))
                  .splits(
                      List.of(
                          com.splitz.expense.model.ExpenseSplit.builder()
                              .userId(friendId)
                              .shareAmount(new BigDecimal("10.00"))
                              .build()))
                  .build());
      when(expenseRepository.findByGroupId(10L)).thenReturn(expenses);
      when(paymentRepository.findByGroupId(10L)).thenReturn(Collections.emptyList());

      FriendBalanceResponseDTO result = engine.getNetBalanceWithFriend(userId, friendId);

      assertThat(result).isNotNull();
      assertThat(result.getNetBalance()).isEqualByComparingTo("10.00");
      assertThat(result.getGroupBalances()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("calculation primitives and aggregations")
  class CalculationPrimitiveTests {

    @Test
    @DisplayName("calculateUserBalanceInGroup aggregates from user group queries")
    void calculatesUserBalanceInGroup() {
      Long userId = 1L;
      Long groupId = 10L;

      when(expenseRepository.calculateTotalPaidByUsersInGroups(List.of(userId), List.of(groupId)))
          .thenReturn(List.of(aggregate(userId, groupId, "50.00")));
      when(expenseRepository.calculateTotalShareForUsersInGroups(List.of(userId), List.of(groupId)))
          .thenReturn(List.of(aggregate(userId, groupId, "20.00")));
      when(paymentRepository.calculateTotalPaymentsPaidInGroups(
              List.of(userId),
              List.of(groupId),
              List.of(SettlementStatus.COMPLETED, SettlementStatus.MARKED_PAID)))
          .thenReturn(List.of(aggregate(userId, groupId, "10.00")));
      when(paymentRepository.calculateTotalPaymentsReceivedInGroups(
              List.of(userId),
              List.of(groupId),
              List.of(SettlementStatus.COMPLETED, SettlementStatus.MARKED_PAID)))
          .thenReturn(List.of(aggregate(userId, groupId, "5.00")));

      BigDecimal balance = engine.calculateUserBalanceInGroup(userId, groupId);
      assertThat(balance).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("calculateBalancesInGroups aggregates batch user group queries")
    void calculatesBalancesInGroups() {
      List<Long> userIds = List.of(1L, 2L);
      List<Long> groupIds = List.of(10L, 20L);

      when(expenseRepository.calculateTotalPaidByUsersInGroups(userIds, groupIds))
          .thenReturn(List.of(aggregate(1L, 10L, "50.00")));
      when(expenseRepository.calculateTotalShareForUsersInGroups(userIds, groupIds))
          .thenReturn(List.of(aggregate(1L, 10L, "20.00")));
      when(paymentRepository.calculateTotalPaymentsPaidInGroups(
              userIds, groupIds, List.of(SettlementStatus.COMPLETED, SettlementStatus.MARKED_PAID)))
          .thenReturn(List.of(aggregate(1L, 10L, "10.00")));
      when(paymentRepository.calculateTotalPaymentsReceivedInGroups(
              userIds, groupIds, List.of(SettlementStatus.COMPLETED, SettlementStatus.MARKED_PAID)))
          .thenReturn(List.of(aggregate(1L, 10L, "5.00")));

      var result = engine.calculateBalancesInGroups(userIds, groupIds);
      assertThat(result.get(1L).get(10L)).isEqualByComparingTo("35.00");
      assertThat(result.get(1L).get(20L)).isEqualByComparingTo("0.00");
    }

    private UserGroupAggregate aggregate(Long userId, Long groupId, String total) {
      return new UserGroupAggregate() {
        @Override
        public Long getUserId() {
          return userId;
        }

        @Override
        public Long getGroupId() {
          return groupId;
        }

        @Override
        public BigDecimal getTotal() {
          return new BigDecimal(total);
        }
      };
    }
  }
}
