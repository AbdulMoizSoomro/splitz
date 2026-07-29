package com.splitz.expense.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.splitz.expense.model.DebtSimplificationPlan;
import com.splitz.expense.model.PlanStatus;
import com.splitz.expense.model.SimplifiedDebtTransaction;
import com.splitz.expense.model.TransactionStatus;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GreedyDebtSimplifierTest {

  private GreedyDebtSimplifier greedyDebtSimplifier;

  @BeforeEach
  void setUp() {
    greedyDebtSimplifier = new GreedyDebtSimplifierImpl();
  }

  @Test
  @DisplayName("3-User cyclic debt resolves to zero simplified transactions")
  void testThreeUserCyclicDebt() {
    // User 1 owes User 2 $100, User 2 owes User 3 $100, User 3 owes User 1 $100
    // Net balances: User 1 = 0, User 2 = 0, User 3 = 0
    Map<Long, BigDecimal> netBalances = new HashMap<>();
    netBalances.put(1L, BigDecimal.ZERO);
    netBalances.put(2L, BigDecimal.ZERO);
    netBalances.put(3L, BigDecimal.ZERO);

    Map<Long, String> usernames = Map.of(1L, "alice", 2L, "bob", 3L, "charlie");

    DebtSimplificationPlan plan =
        greedyDebtSimplifier.simplifyDebts(100L, netBalances, Set.of(), usernames, 3);

    assertThat(plan).isNotNull();
    assertThat(plan.getGroupId()).isEqualTo(100L);
    assertThat(plan.getStatus()).isEqualTo(PlanStatus.PROPOSED);
    assertThat(plan.getOriginalTransactionCount()).isEqualTo(3);
    assertThat(plan.getSimplifiedTransactionCount()).isEqualTo(0);
    assertThat(plan.getTotalDebtVolume()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(plan.getTransactions()).isEmpty();
  }

  @Test
  @DisplayName("3-User linear chain (A->B->C) simplifies to a single direct transfer (A->C)")
  void testThreeUserLinearChain() {
    // User 1 owes User 2 $50, User 2 owes User 3 $50
    // Net balances: User 1 (-50), User 2 (0), User 3 (+50)
    Map<Long, BigDecimal> netBalances = new HashMap<>();
    netBalances.put(1L, new BigDecimal("-50.00"));
    netBalances.put(2L, BigDecimal.ZERO);
    netBalances.put(3L, new BigDecimal("50.00"));

    Map<Long, String> usernames = Map.of(1L, "alice", 2L, "bob", 3L, "charlie");

    DebtSimplificationPlan plan =
        greedyDebtSimplifier.simplifyDebts(100L, netBalances, Set.of(), usernames, 2);

    assertThat(plan).isNotNull();
    assertThat(plan.getSimplifiedTransactionCount()).isEqualTo(1);
    assertThat(plan.getTotalDebtVolume()).isEqualByComparingTo(new BigDecimal("50.00"));

    SimplifiedDebtTransaction tx = plan.getTransactions().get(0);
    assertThat(tx.getFromUserId()).isEqualTo(1L);
    assertThat(tx.getFromUsername()).isEqualTo("alice");
    assertThat(tx.getToUserId()).isEqualTo(3L);
    assertThat(tx.getToUsername()).isEqualTo("charlie");
    assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
  }

  @Test
  @DisplayName("5-User complex debt network produces at most N-1 transactions")
  void testFiveUserComplexDebtNetwork() {
    // Net balances: User 1 (-100), User 2 (-50), User 3 (+30), User 4 (+40), User 5 (+80)
    Map<Long, BigDecimal> netBalances = new HashMap<>();
    netBalances.put(1L, new BigDecimal("-100.00"));
    netBalances.put(2L, new BigDecimal("-50.00"));
    netBalances.put(3L, new BigDecimal("30.00"));
    netBalances.put(4L, new BigDecimal("40.00"));
    netBalances.put(5L, new BigDecimal("80.00"));

    Map<Long, String> usernames =
        Map.of(1L, "user1", 2L, "user2", 3L, "user3", 4L, "user4", 5L, "user5");

    DebtSimplificationPlan plan =
        greedyDebtSimplifier.simplifyDebts(101L, netBalances, Set.of(), usernames, 7);

    assertThat(plan.getSimplifiedTransactionCount()).isLessThanOrEqualTo(4);
    assertThat(plan.getTotalDebtVolume()).isEqualByComparingTo(new BigDecimal("150.00"));

    // Verify net balances reconstructed from simplified transactions match original net positions
    Map<Long, BigDecimal> reconstructed = new HashMap<>();
    for (Long id : netBalances.keySet()) {
      reconstructed.put(id, BigDecimal.ZERO);
    }
    for (SimplifiedDebtTransaction tx : plan.getTransactions()) {
      reconstructed.put(
          tx.getFromUserId(), reconstructed.get(tx.getFromUserId()).subtract(tx.getAmount()));
      reconstructed.put(tx.getToUserId(), reconstructed.get(tx.getToUserId()).add(tx.getAmount()));
    }

    for (Long id : netBalances.keySet()) {
      assertThat(reconstructed.get(id))
          .as("Reconstructed net balance for user %d", id)
          .isEqualByComparingTo(netBalances.get(id));
    }
  }

  @Test
  @DisplayName("Zero balances return plan with zero transactions")
  void testZeroBalances() {
    Map<Long, BigDecimal> netBalances = Map.of(1L, BigDecimal.ZERO, 2L, BigDecimal.ZERO);

    DebtSimplificationPlan plan =
        greedyDebtSimplifier.simplifyDebts(102L, netBalances, Set.of(), Map.of(), 0);

    assertThat(plan.getSimplifiedTransactionCount()).isEqualTo(0);
    assertThat(plan.getTotalDebtVolume()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(plan.getTransactions()).isEmpty();
  }

  @Test
  @DisplayName("Opt-out exclusions skip graph edge reductions involving opted-out users")
  void testOptOutExclusions() {
    // Net balances: User 1 (-100), User 2 (-50), User 3 (+150)
    // User 1 is opted out
    Map<Long, BigDecimal> netBalances = new HashMap<>();
    netBalances.put(1L, new BigDecimal("-100.00"));
    netBalances.put(2L, new BigDecimal("-50.00"));
    netBalances.put(3L, new BigDecimal("150.00"));

    Set<Long> optedOutUsers = new HashSet<>();
    optedOutUsers.add(1L);

    Map<Long, String> usernames = Map.of(1L, "alice", 2L, "bob", 3L, "charlie");

    DebtSimplificationPlan plan =
        greedyDebtSimplifier.simplifyDebts(103L, netBalances, optedOutUsers, usernames, 3);

    assertThat(plan.getOptedOutUserIds()).contains(1L);
    // User 1 was opted out, so User 1 is excluded from greedy simplification.
    // User 2 (-50) matches User 3 (+150) for $50. User 1 (-100) is excluded.
    assertThat(plan.getTransactions()).hasSize(1);
    SimplifiedDebtTransaction tx = plan.getTransactions().get(0);
    assertThat(tx.getFromUserId()).isEqualTo(2L);
    assertThat(tx.getToUserId()).isEqualTo(3L);
    assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
  }
}
