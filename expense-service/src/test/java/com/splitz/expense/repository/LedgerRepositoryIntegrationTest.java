package com.splitz.expense.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.PaymentType;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LedgerRepositoryIntegrationTest {

  @Autowired private LedgerRepository ledgerRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GroupMemberRepository groupMemberRepository;
  @Autowired private ExpenseRepository expenseRepository;
  @Autowired private PaymentRepository paymentRepository;

  private Group group;

  @BeforeEach
  void setUp() {
    group = groupRepository.save(Group.builder().name("Trip Group").createdBy(1L).build());

    groupMemberRepository.save(
        GroupMember.builder().group(group).userId(1L).role(GroupRole.ADMIN).build());
    groupMemberRepository.save(
        GroupMember.builder().group(group).userId(2L).role(GroupRole.MEMBER).build());
    groupMemberRepository.save(
        GroupMember.builder().group(group).userId(3L).role(GroupRole.MEMBER).build());
  }

  @AfterEach
  void tearDown() {
    paymentRepository.deleteAll();
    expenseRepository.deleteAll();
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
  }

  @Test
  void testCalculateGroupBalances() {
    // User 1 pays 60.00, split 20.00 each among users 1, 2, 3
    Expense expense =
        Expense.builder()
            .group(group)
            .paidBy(1L)
            .amount(new BigDecimal("60.00"))
            .currency("EUR")
            .description("Dinner")
            .build();
    expense.setSplits(
        List.of(
            ExpenseSplit.builder()
                .expense(expense)
                .userId(1L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("20.00"))
                .build(),
            ExpenseSplit.builder()
                .expense(expense)
                .userId(2L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("20.00"))
                .build(),
            ExpenseSplit.builder()
                .expense(expense)
                .userId(3L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("20.00"))
                .build()));
    expenseRepository.save(expense);

    // User 2 pays User 1 20.00 (settlement)
    Payment payment =
        Payment.builder()
            .type(PaymentType.GROUP)
            .groupId(group.getId())
            .payerId(2L)
            .payeeId(1L)
            .amount(new BigDecimal("20.00"))
            .status(SettlementStatus.COMPLETED)
            .build();
    paymentRepository.save(payment);

    // Call LedgerRepository
    List<UserBalanceAggregate> results = ledgerRepository.calculateGroupBalances(group.getId());
    Map<Long, BigDecimal> balanceMap =
        results.stream()
            .collect(
                Collectors.toMap(
                    UserBalanceAggregate::getUserId, UserBalanceAggregate::getBalance));

    // User 1: +60 (paid expense) - 20 (share) - 20 (payment received) = +20.00
    assertThat(balanceMap.get(1L)).isEqualByComparingTo(new BigDecimal("20.00"));
    // User 2: -20 (share) + 20 (payment paid) = 0.00
    assertThat(balanceMap.get(2L)).isEqualByComparingTo(new BigDecimal("0.00"));
    // User 3: -20 (share) = -20.00
    assertThat(balanceMap.get(3L)).isEqualByComparingTo(new BigDecimal("-20.00"));
  }

  @Test
  void testCalculatePairwiseBalance() {
    // User 1 pays 60.00, split 20.00 each among users 1, 2, 3
    Expense expense =
        Expense.builder()
            .group(group)
            .paidBy(1L)
            .amount(new BigDecimal("60.00"))
            .currency("EUR")
            .description("Dinner")
            .build();
    expense.setSplits(
        List.of(
            ExpenseSplit.builder()
                .expense(expense)
                .userId(1L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("20.00"))
                .build(),
            ExpenseSplit.builder()
                .expense(expense)
                .userId(2L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("20.00"))
                .build(),
            ExpenseSplit.builder()
                .expense(expense)
                .userId(3L)
                .splitType(SplitType.EQUAL)
                .shareAmount(new BigDecimal("20.00"))
                .build()));
    expenseRepository.save(expense);

    // User 2 pays User 1 5.00
    Payment payment =
        Payment.builder()
            .type(PaymentType.GROUP)
            .groupId(group.getId())
            .payerId(2L)
            .payeeId(1L)
            .amount(new BigDecimal("5.00"))
            .status(SettlementStatus.COMPLETED)
            .build();
    paymentRepository.save(payment);

    BigDecimal user1WithUser2 =
        ledgerRepository.calculatePairwiseBalanceInGroup(1L, 2L, group.getId());
    BigDecimal user2WithUser1 =
        ledgerRepository.calculatePairwiseBalanceInGroup(2L, 1L, group.getId());
    BigDecimal user1WithUser3 =
        ledgerRepository.calculatePairwiseBalanceInGroup(1L, 3L, group.getId());
    BigDecimal user2WithUser3 =
        ledgerRepository.calculatePairwiseBalanceInGroup(2L, 3L, group.getId());

    // User 2 owes User 1 20 - 5 = 15.00
    assertThat(user1WithUser2).isEqualByComparingTo(new BigDecimal("15.00"));
    assertThat(user2WithUser1).isEqualByComparingTo(new BigDecimal("-15.00"));
    // User 3 owes User 1 20.00
    assertThat(user1WithUser3).isEqualByComparingTo(new BigDecimal("20.00"));
    // No transaction between User 2 and User 3
    assertThat(user2WithUser3).isEqualByComparingTo(BigDecimal.ZERO);
  }
}
