package com.splitz.expense.balance;

import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.PaymentRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import com.splitz.expense.repository.UserGroupAggregate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultDebtBalanceEngine implements DebtBalanceEngine {

  private static final List<SettlementStatus> SETTLEMENT_STATUSES =
      List.of(SettlementStatus.COMPLETED, SettlementStatus.MARKED_PAID);

  private final ExpenseRepository expenseRepository;
  private final SettlementAllocationRepository settlementAllocationRepository;
  private final PaymentRepository paymentRepository;

  @Override
  public Map<Long, BigDecimal> calculateGroupBalances(
      List<Long> memberIds, List<Expense> expenses, List<SettlementAllocation> allocations) {
    Map<Long, BigDecimal> balances = new java.util.HashMap<>();
    for (Long memberId : memberIds) {
      balances.put(memberId, BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    for (Expense expense : expenses) {
      Long payerId = expense.getPaidBy();
      BigDecimal amount = expense.getAmount();
      if (payerId != null && amount != null) {
        balances.put(payerId, balances.getOrDefault(payerId, BigDecimal.ZERO).add(amount));
      }

      if (expense.getSplits() != null) {
        for (ExpenseSplit split : expense.getSplits()) {
          Long userId = split.getUserId();
          BigDecimal share = split.getShareAmount();
          if (userId != null && share != null) {
            balances.put(userId, balances.getOrDefault(userId, BigDecimal.ZERO).subtract(share));
          }
        }
      }
    }

    if (allocations != null) {
      for (SettlementAllocation allocation : allocations) {
        Payment payment = allocation.getPayment();
        if (payment != null) {
          if (payment.getStatus() == SettlementStatus.COMPLETED
              || payment.getStatus() == SettlementStatus.MARKED_PAID) {
            Long payerId = payment.getPayerId();
            Long payeeId = payment.getPayeeId();
            BigDecimal amount = allocation.getAmount();
            if (payerId != null && payeeId != null && amount != null) {
              balances.put(payerId, balances.getOrDefault(payerId, BigDecimal.ZERO).add(amount));
              balances.put(
                  payeeId, balances.getOrDefault(payeeId, BigDecimal.ZERO).subtract(amount));
            }
          }
        }
      }
    }

    return balances;
  }

  @Override
  public BigDecimal calculateNetBalanceInGroup(
      Long userId, Long friendId, List<Expense> expenses, List<SettlementAllocation> allocations) {
    BigDecimal netBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    if (expenses != null) {
      for (Expense expense : expenses) {
        Long payerId = expense.getPaidBy();
        if (payerId != null && expense.getSplits() != null) {
          if (payerId.equals(userId)) {
            for (ExpenseSplit split : expense.getSplits()) {
              if (friendId.equals(split.getUserId()) && split.getShareAmount() != null) {
                netBalance = netBalance.add(split.getShareAmount());
              }
            }
          } else if (payerId.equals(friendId)) {
            for (ExpenseSplit split : expense.getSplits()) {
              if (userId.equals(split.getUserId()) && split.getShareAmount() != null) {
                netBalance = netBalance.subtract(split.getShareAmount());
              }
            }
          }
        }
      }
    }

    if (allocations != null) {
      for (SettlementAllocation allocation : allocations) {
        Payment payment = allocation.getPayment();
        if (payment != null) {
          if (payment.getStatus() == SettlementStatus.COMPLETED
              || payment.getStatus() == SettlementStatus.MARKED_PAID) {
            Long payerId = payment.getPayerId();
            Long payeeId = payment.getPayeeId();
            BigDecimal amount = allocation.getAmount();
            if (payerId != null && payeeId != null && amount != null) {
              if (payerId.equals(userId) && payeeId.equals(friendId)) {
                netBalance = netBalance.add(amount);
              } else if (payerId.equals(friendId) && payeeId.equals(userId)) {
                netBalance = netBalance.subtract(amount);
              }
            }
          }
        }
      }
    }

    return netBalance;
  }

  @Override
  public BigDecimal calculateUserBalanceInGroup(Long userId, Long groupId) {
    return calculateBalancesInGroups(List.of(userId), List.of(groupId)).get(userId).get(groupId);
  }

  @Override
  public Map<Long, Map<Long, BigDecimal>> calculateBalancesInGroups(
      Collection<Long> userIds, Collection<Long> groupIds) {
    Map<Long, Map<Long, BigDecimal>> paid =
        aggregate(expenseRepository.calculateTotalPaidByUsersInGroups(userIds, groupIds));
    Map<Long, Map<Long, BigDecimal>> share =
        aggregate(expenseRepository.calculateTotalShareForUsersInGroups(userIds, groupIds));
    Map<Long, Map<Long, BigDecimal>> settlementsPaid =
        aggregate(
            settlementAllocationRepository.calculateTotalSettlementsPaidByUsersInGroups(
                userIds, groupIds, SETTLEMENT_STATUSES));
    Map<Long, Map<Long, BigDecimal>> settlementsReceived =
        aggregate(
            settlementAllocationRepository.calculateTotalSettlementsReceivedByUsersInGroups(
                userIds, groupIds, SETTLEMENT_STATUSES));

    Map<Long, Map<Long, BigDecimal>> balances = new HashMap<>();
    for (Long userId : userIds) {
      Map<Long, BigDecimal> byGroup = new HashMap<>();
      for (Long groupId : groupIds) {
        byGroup.put(
            groupId,
            amountOrZero(paid, userId, groupId)
                .subtract(amountOrZero(share, userId, groupId))
                .add(amountOrZero(settlementsPaid, userId, groupId))
                .subtract(amountOrZero(settlementsReceived, userId, groupId))
                .setScale(2, RoundingMode.HALF_UP));
      }
      balances.put(userId, byGroup);
    }
    return balances;
  }

  @Override
  public BigDecimal calculateGlobalSettlementBalance(Long userId, Long friendId) {
    BigDecimal userGlobalSettled =
        safe(settlementAllocationRepository.calculateTotalSettledBetweenUsersInGroup(
                userId, friendId, null, SettlementStatus.COMPLETED))
            .add(
                safe(
                    settlementAllocationRepository.calculateTotalSettledBetweenUsersInGroup(
                        userId, friendId, null, SettlementStatus.MARKED_PAID)));
    BigDecimal friendGlobalSettled =
        safe(settlementAllocationRepository.calculateTotalSettledBetweenUsersInGroup(
                friendId, userId, null, SettlementStatus.COMPLETED))
            .add(
                safe(
                    settlementAllocationRepository.calculateTotalSettledBetweenUsersInGroup(
                        friendId, userId, null, SettlementStatus.MARKED_PAID)));

    return userGlobalSettled.subtract(friendGlobalSettled).setScale(2, RoundingMode.HALF_UP);
  }

  @Override
  public BigDecimal calculateUserGlobalSettlementBalance(Long userId) {
    List<Payment> globalPayments = paymentRepository.findByPayerIdOrPayeeId(userId, userId);
    BigDecimal totalBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    for (Payment payment : globalPayments) {
      if (payment.getStatus() == SettlementStatus.COMPLETED
          || payment.getStatus() == SettlementStatus.MARKED_PAID) {
        for (SettlementAllocation allocation : payment.getAllocations()) {
          if (allocation.getGroupId() == null) {
            if (payment.getPayerId().equals(userId)) {
              totalBalance = totalBalance.add(allocation.getAmount());
            } else {
              totalBalance = totalBalance.subtract(allocation.getAmount());
            }
          }
        }
      }
    }
    return totalBalance;
  }

  private BigDecimal safe(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  private static Map<Long, Map<Long, BigDecimal>> aggregate(List<UserGroupAggregate> rows) {
    Map<Long, Map<Long, BigDecimal>> matrix = new HashMap<>();
    for (UserGroupAggregate row : rows) {
      matrix
          .computeIfAbsent(row.getUserId(), key -> new HashMap<>())
          .put(row.getGroupId(), row.getTotal());
    }
    return matrix;
  }

  private static BigDecimal amountOrZero(
      Map<Long, Map<Long, BigDecimal>> matrix, Long userId, Long groupId) {
    Map<Long, BigDecimal> byGroup = matrix.get(userId);
    BigDecimal total = byGroup != null ? byGroup.get(groupId) : null;
    return total != null ? total : BigDecimal.ZERO;
  }
}
