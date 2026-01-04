package com.splitz.expense.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.splitz.expense.dto.BalanceDTO;
import com.splitz.expense.dto.DebtDTO;
import com.splitz.expense.dto.GroupBalanceResponseDTO;
import com.splitz.expense.dto.UserBalanceResponseDTO;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private BalanceService balanceService;

    private Group group;
    private GroupMember member1;
    private GroupMember member2;
    private GroupMember member3;

    @BeforeEach
    void setUp() {
        group = Group.builder().id(1L).name("Test Group").build();
        member1 = GroupMember.builder().id(1L).group(group).userId(101L).build();
        member2 = GroupMember.builder().id(2L).group(group).userId(102L).build();
        member3 = GroupMember.builder().id(3L).group(group).userId(103L).build();
    }

    @Test
    void getGroupBalances_NoExpenses_ReturnsZeroBalances() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMemberRepository.findByGroupId(1L)).thenReturn(Arrays.asList(member1, member2));
        when(expenseRepository.findByGroupId(1L)).thenReturn(Collections.emptyList());

        GroupBalanceResponseDTO response = balanceService.getGroupBalances(1L);

        assertNotNull(response);
        assertEquals(1L, response.getGroupId());
        assertEquals(2, response.getBalances().size());
        assertTrue(
                response.getBalances().stream()
                        .allMatch(b -> b.getBalance().compareTo(BigDecimal.ZERO) == 0));
        assertTrue(response.getSimplifiedDebts().isEmpty());
    }

    @Test
    void getGroupBalances_SimpleEqualSplit() {
        // Expense: 60 EUR paid by User 101, split EQUAL among 101, 102, 103
        Expense expense
                = Expense.builder().id(1L).group(group).amount(new BigDecimal("60.00")).paidBy(101L).build();

        ExpenseSplit split1
                = ExpenseSplit.builder()
                        .userId(101L)
                        .shareAmount(new BigDecimal("20.00"))
                        .expense(expense)
                        .build();
        ExpenseSplit split2
                = ExpenseSplit.builder()
                        .userId(102L)
                        .shareAmount(new BigDecimal("20.00"))
                        .expense(expense)
                        .build();
        ExpenseSplit split3
                = ExpenseSplit.builder()
                        .userId(103L)
                        .shareAmount(new BigDecimal("20.00"))
                        .expense(expense)
                        .build();
        expense.setSplits(Arrays.asList(split1, split2, split3));

        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMemberRepository.findByGroupId(1L))
                .thenReturn(Arrays.asList(member1, member2, member3));
        when(expenseRepository.findByGroupId(1L)).thenReturn(Collections.singletonList(expense));

        GroupBalanceResponseDTO response = balanceService.getGroupBalances(1L);

        assertNotNull(response);
        // User 101: paid 60, owes 20 -> balance +40
        // User 102: paid 0, owes 20 -> balance -20
        // User 103: paid 0, owes 20 -> balance -20

        BalanceDTO b1 = findBalance(response.getBalances(), 101L);
        BalanceDTO b2 = findBalance(response.getBalances(), 102L);
        BalanceDTO b3 = findBalance(response.getBalances(), 103L);

        assertEquals(0, new BigDecimal("40.00").compareTo(b1.getBalance()));
        assertEquals(0, new BigDecimal("-20.00").compareTo(b2.getBalance()));
        assertEquals(0, new BigDecimal("-20.00").compareTo(b3.getBalance()));

        assertEquals(2, response.getSimplifiedDebts().size());
        // Debts: 102 -> 101 (20), 103 -> 101 (20)
        assertTrue(hasDebt(response.getSimplifiedDebts(), 102L, 101L, new BigDecimal("20.00")));
        assertTrue(hasDebt(response.getSimplifiedDebts(), 103L, 101L, new BigDecimal("20.00")));
    }

    @Test
    void getGroupBalances_MultipleExpenses() {
        // Expense 1: 60 EUR paid by 101, split among 101, 102, 103 (20 each)
        Expense e1
                = Expense.builder().id(1L).group(group).amount(new BigDecimal("60.00")).paidBy(101L).build();
        e1.setSplits(
                Arrays.asList(
                        ExpenseSplit.builder()
                                .userId(101L)
                                .shareAmount(new BigDecimal("20.00"))
                                .expense(e1)
                                .build(),
                        ExpenseSplit.builder()
                                .userId(102L)
                                .shareAmount(new BigDecimal("20.00"))
                                .expense(e1)
                                .build(),
                        ExpenseSplit.builder()
                                .userId(103L)
                                .shareAmount(new BigDecimal("20.00"))
                                .expense(e1)
                                .build()));

        // Expense 2: 30 EUR paid by 102, split among 101, 102 (15 each)
        Expense e2
                = Expense.builder().id(2L).group(group).amount(new BigDecimal("30.00")).paidBy(102L).build();
        e2.setSplits(
                Arrays.asList(
                        ExpenseSplit.builder()
                                .userId(101L)
                                .shareAmount(new BigDecimal("15.00"))
                                .expense(e2)
                                .build(),
                        ExpenseSplit.builder()
                                .userId(102L)
                                .shareAmount(new BigDecimal("15.00"))
                                .expense(e2)
                                .build()));

        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMemberRepository.findByGroupId(1L))
                .thenReturn(Arrays.asList(member1, member2, member3));
        when(expenseRepository.findByGroupId(1L)).thenReturn(Arrays.asList(e1, e2));

        GroupBalanceResponseDTO response = balanceService.getGroupBalances(1L);

        // User 101: paid 60, owes (20+15)=35 -> balance +25
        // User 102: paid 30, owes (20+15)=35 -> balance -5
        // User 103: paid 0, owes 20 -> balance -20
        BalanceDTO b1 = findBalance(response.getBalances(), 101L);
        BalanceDTO b2 = findBalance(response.getBalances(), 102L);
        BalanceDTO b3 = findBalance(response.getBalances(), 103L);

        assertEquals(0, new BigDecimal("25.00").compareTo(b1.getBalance()));
        assertEquals(0, new BigDecimal("-5.00").compareTo(b2.getBalance()));
        assertEquals(0, new BigDecimal("-20.00").compareTo(b3.getBalance()));

        // Simplified Debts: 102 -> 101 (5), 103 -> 101 (20)
        assertEquals(2, response.getSimplifiedDebts().size());
        assertTrue(hasDebt(response.getSimplifiedDebts(), 102L, 101L, new BigDecimal("5.00")));
        assertTrue(hasDebt(response.getSimplifiedDebts(), 103L, 101L, new BigDecimal("20.00")));
    }

    @Test
    void getUserBalances_MultipleGroups() {
        Group group2 = Group.builder().id(2L).name("Group 2").build();
        GroupMember member1InG2 = GroupMember.builder().id(4L).group(group2).userId(101L).build();

        when(groupMemberRepository.findByUserId(101L)).thenReturn(Arrays.asList(member1, member1InG2));

        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupRepository.existsById(2L)).thenReturn(true);
        when(groupMemberRepository.findByGroupId(1L))
                .thenReturn(Arrays.asList(member1, member2, member3));
        when(groupMemberRepository.findByGroupId(2L)).thenReturn(Arrays.asList(member1InG2));

        // Group 1: User 101 has +25 balance (from previous test case)
        Expense e1
                = Expense.builder().id(1L).group(group).amount(new BigDecimal("60.00")).paidBy(101L).build();
        e1.setSplits(
                Arrays.asList(
                        ExpenseSplit.builder()
                                .userId(101L)
                                .shareAmount(new BigDecimal("20.00"))
                                .expense(e1)
                                .build(),
                        ExpenseSplit.builder()
                                .userId(102L)
                                .shareAmount(new BigDecimal("20.00"))
                                .expense(e1)
                                .build(),
                        ExpenseSplit.builder()
                                .userId(103L)
                                .shareAmount(new BigDecimal("20.00"))
                                .expense(e1)
                                .build()));
        Expense e2
                = Expense.builder().id(2L).group(group).amount(new BigDecimal("30.00")).paidBy(102L).build();
        e2.setSplits(
                Arrays.asList(
                        ExpenseSplit.builder()
                                .userId(101L)
                                .shareAmount(new BigDecimal("15.00"))
                                .expense(e2)
                                .build(),
                        ExpenseSplit.builder()
                                .userId(102L)
                                .shareAmount(new BigDecimal("15.00"))
                                .expense(e2)
                                .build()));
        when(expenseRepository.findByGroupId(1L)).thenReturn(Arrays.asList(e1, e2));

        // Group 2: User 101 has -10 balance
        Expense e3
                = Expense.builder().id(3L).group(group2).amount(new BigDecimal("20.00")).paidBy(104L).build();
        e3.setSplits(
                Arrays.asList(
                        ExpenseSplit.builder()
                                .userId(101L)
                                .shareAmount(new BigDecimal("10.00"))
                                .expense(e3)
                                .build(),
                        ExpenseSplit.builder()
                                .userId(104L)
                                .shareAmount(new BigDecimal("10.00"))
                                .expense(e3)
                                .build()));
        when(expenseRepository.findByGroupId(2L)).thenReturn(Collections.singletonList(e3));

        UserBalanceResponseDTO response = balanceService.getUserBalances(101L);

        assertNotNull(response);
        assertEquals(101L, response.getUserId());
        assertEquals(0, new BigDecimal("15.00").compareTo(response.getTotalBalance())); // 25 - 10 = 15
        assertEquals(2, response.getGroupBalances().size());

        UserBalanceResponseDTO.GroupBalanceDTO gb1 = findGroupBalance(response.getGroupBalances(), 1L);
        UserBalanceResponseDTO.GroupBalanceDTO gb2 = findGroupBalance(response.getGroupBalances(), 2L);

        assertEquals(0, new BigDecimal("25.00").compareTo(gb1.getBalance()));
        assertEquals(0, new BigDecimal("-10.00").compareTo(gb2.getBalance()));
    }

    private BalanceDTO findBalance(List<BalanceDTO> balances, Long userId) {
        return balances.stream().filter(b -> b.getUserId().equals(userId)).findFirst().orElseThrow();
    }

    private UserBalanceResponseDTO.GroupBalanceDTO findGroupBalance(
            List<UserBalanceResponseDTO.GroupBalanceDTO> balances, Long groupId) {
        return balances.stream().filter(b -> b.getGroupId().equals(groupId)).findFirst().orElseThrow();
    }

    private boolean hasDebt(List<DebtDTO> debts, Long from, Long to, BigDecimal amount) {
        return debts.stream()
                .anyMatch(
                        d
                        -> d.getFrom().equals(from)
                        && d.getTo().equals(to)
                        && d.getAmount().compareTo(amount) == 0);
    }
}
