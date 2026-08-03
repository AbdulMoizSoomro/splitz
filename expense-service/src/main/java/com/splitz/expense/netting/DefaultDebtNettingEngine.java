package com.splitz.expense.netting;

import com.splitz.expense.model.DebtSimplificationPlan;
import com.splitz.expense.model.PlanStatus;
import com.splitz.expense.model.SimplifiedDebtTransaction;
import com.splitz.expense.model.TransactionStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Deep Debt-Netting Engine: deterministic greedy priority-queue netting producing a plan. */
@Component
public class DefaultDebtNettingEngine implements DebtNettingEngine {

  @Override
  public DebtSimplificationPlan simplifyDebts(
      Long groupId,
      Map<Long, BigDecimal> netBalances,
      Set<Long> optedOutUserIds,
      Map<Long, String> usernames,
      int originalTransactionCount) {

    Set<Long> optOuts =
        optedOutUserIds != null ? new HashSet<>(optedOutUserIds) : Collections.emptySet();
    Map<Long, String> nameMap = usernames != null ? usernames : Collections.emptyMap();

    DebtSimplificationPlan plan =
        DebtSimplificationPlan.builder()
            .groupId(groupId)
            .status(PlanStatus.PROPOSED)
            .originalTransactionCount(originalTransactionCount)
            .optedOutUserIds(optOuts)
            .transactions(new ArrayList<>())
            .totalDebtVolume(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .build();

    if (netBalances == null || netBalances.isEmpty()) {
      plan.setSimplifiedTransactionCount(0);
      return plan;
    }

    // Debtors (balance < 0): sorted by magnitude descending, then userId ascending for determinism
    PriorityQueue<UserBalanceNode> debtors =
        new PriorityQueue<>(
            (a, b) -> {
              int cmp = b.amount.abs().compareTo(a.amount.abs());
              if (cmp != 0) {
                return cmp;
              }
              return a.userId.compareTo(b.userId);
            });

    // Creditors (balance > 0): sorted by magnitude descending, then userId ascending for
    // determinism
    PriorityQueue<UserBalanceNode> creditors =
        new PriorityQueue<>(
            (a, b) -> {
              int cmp = b.amount.compareTo(a.amount);
              if (cmp != 0) {
                return cmp;
              }
              return a.userId.compareTo(b.userId);
            });

    netBalances.forEach(
        (userId, balance) -> {
          if (balance != null) {
            BigDecimal scaled = balance.setScale(2, RoundingMode.HALF_UP);
            if (scaled.compareTo(BigDecimal.ZERO) < 0) {
              if (!optOuts.contains(userId)) {
                debtors.add(new UserBalanceNode(userId, scaled));
              }
            } else if (scaled.compareTo(BigDecimal.ZERO) > 0) {
              if (!optOuts.contains(userId)) {
                creditors.add(new UserBalanceNode(userId, scaled));
              }
            }
          }
        });

    List<SimplifiedDebtTransaction> transactions = new ArrayList<>();
    BigDecimal totalVolume = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    while (!debtors.isEmpty() && !creditors.isEmpty()) {
      UserBalanceNode debtor = debtors.poll();
      UserBalanceNode creditor = creditors.poll();

      BigDecimal debtMagnitude = debtor.amount.abs();
      BigDecimal creditMagnitude = creditor.amount;
      BigDecimal settleAmount =
          debtMagnitude.min(creditMagnitude).setScale(2, RoundingMode.HALF_UP);

      if (settleAmount.compareTo(BigDecimal.ZERO) > 0) {
        SimplifiedDebtTransaction tx =
            SimplifiedDebtTransaction.builder()
                .plan(plan)
                .fromUserId(debtor.userId)
                .fromUsername(nameMap.get(debtor.userId))
                .toUserId(creditor.userId)
                .toUsername(nameMap.get(creditor.userId))
                .amount(settleAmount)
                .status(TransactionStatus.PENDING)
                .build();

        transactions.add(tx);
        totalVolume = totalVolume.add(settleAmount);

        debtor.amount = debtor.amount.add(settleAmount);
        creditor.amount = creditor.amount.subtract(settleAmount);

        if (debtor.amount.compareTo(BigDecimal.ZERO) < 0) {
          debtors.add(debtor);
        }
        if (creditor.amount.compareTo(BigDecimal.ZERO) > 0) {
          creditors.add(creditor);
        }
      }
    }

    plan.setTransactions(transactions);
    plan.setSimplifiedTransactionCount(transactions.size());
    plan.setTotalDebtVolume(totalVolume);

    return plan;
  }

  private static class UserBalanceNode {
    final Long userId;
    BigDecimal amount;

    UserBalanceNode(Long userId, BigDecimal amount) {
      this.userId = userId;
      this.amount = amount;
    }
  }
}
