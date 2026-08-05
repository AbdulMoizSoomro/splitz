package com.splitz.expense.balance;

import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultDebtBalanceEngine implements DebtBalanceEngine {

  private final ExpenseRepository expenseRepository;
  private final SettlementAllocationRepository settlementAllocationRepository;

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
    BigDecimal totalPaid = expenseRepository.calculateTotalPaidByUserInGroup(userId, groupId);
    BigDecimal totalShare = expenseRepository.calculateTotalShareForUserInGroup(userId, groupId);
    BigDecimal settlementsPaid =
        settlementAllocationRepository
            .calculateTotalSettlementsPaidByUserInGroup(userId, groupId, SettlementStatus.COMPLETED)
            .add(
                settlementAllocationRepository.calculateTotalSettlementsPaidByUserInGroup(
                    userId, groupId, SettlementStatus.MARKED_PAID));
    BigDecimal settlementsReceived =
        settlementAllocationRepository
            .calculateTotalSettlementsReceivedByUserInGroup(
                userId, groupId, SettlementStatus.COMPLETED)
            .add(
                settlementAllocationRepository.calculateTotalSettlementsReceivedByUserInGroup(
                    userId, groupId, SettlementStatus.MARKED_PAID));

    return (totalPaid != null ? totalPaid : BigDecimal.ZERO)
        .subtract(totalShare != null ? totalShare : BigDecimal.ZERO)
        .add(settlementsPaid != null ? settlementsPaid : BigDecimal.ZERO)
        .subtract(settlementsReceived != null ? settlementsReceived : BigDecimal.ZERO)
        .setScale(2, RoundingMode.HALF_UP);
  }

  @Override
  public BigDecimal calculateGlobalSettlementBalance(Long userId, Long friendId) {
    BigDecimal userGlobalSettled =
        settlementAllocationRepository
            .calculateTotalSettledBetweenUsersInGroup(
                userId, friendId, null, SettlementStatus.COMPLETED)
            .add(
                settlementAllocationRepository.calculateTotalSettledBetweenUsersInGroup(
                    userId, friendId, null, SettlementStatus.MARKED_PAID));
    BigDecimal friendGlobalSettled =
        settlementAllocationRepository
            .calculateTotalSettledBetweenUsersInGroup(
                friendId, userId, null, SettlementStatus.COMPLETED)
            .add(
                settlementAllocationRepository.calculateTotalSettledBetweenUsersInGroup(
                    friendId, userId, null, SettlementStatus.MARKED_PAID));

    return userGlobalSettled.subtract(friendGlobalSettled).setScale(2, RoundingMode.HALF_UP);
  }
}
