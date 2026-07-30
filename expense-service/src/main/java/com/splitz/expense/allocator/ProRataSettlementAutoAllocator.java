package com.splitz.expense.allocator;

import com.splitz.expense.dto.FriendBalanceResponseDTO;
import com.splitz.expense.dto.FriendGroupBalanceDTO;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.service.BalanceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Pro-Rata Settlement Allocator (Wayfinder Issue #71 — Slice 4). Distributes cross-group settlement
 * amounts proportionally across groups based on outstanding group balance magnitudes.
 */
@Component
@Primary
public class ProRataSettlementAutoAllocator implements SettlementAutoAllocator {

  private final BalanceService balanceService;

  public ProRataSettlementAutoAllocator(BalanceService balanceService) {
    this.balanceService = balanceService;
  }

  @Override
  public List<SettlementAllocation> allocate(Long payerId, Long payeeId, BigDecimal amount) {
    List<SettlementAllocation> allocations = new ArrayList<>();

    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      return allocations;
    }

    FriendBalanceResponseDTO balanceResponse =
        balanceService.getNetBalanceWithFriend(payerId, payeeId);

    if (balanceResponse == null || balanceResponse.getGroupBalances() == null) {
      allocations.add(SettlementAllocation.builder().groupId(null).amount(amount).build());
      return allocations;
    }

    // Filter to groups where payer owes payee (payer balance is negative)
    List<FriendGroupBalanceDTO> debtsToSettle =
        balanceResponse.getGroupBalances().stream()
            .filter(gb -> gb.getBalance() != null && gb.getBalance().compareTo(BigDecimal.ZERO) < 0)
            .sorted(Comparator.comparing(FriendGroupBalanceDTO::getGroupId))
            .collect(Collectors.toList());

    if (debtsToSettle.isEmpty()) {
      allocations.add(SettlementAllocation.builder().groupId(null).amount(amount).build());
      return allocations;
    }

    BigDecimal totalOwed =
        debtsToSettle.stream()
            .map(gb -> gb.getBalance().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalOwed.compareTo(BigDecimal.ZERO) == 0) {
      allocations.add(SettlementAllocation.builder().groupId(null).amount(amount).build());
      return allocations;
    }

    if (amount.compareTo(totalOwed) >= 0) {
      // Full settlement of all debts; excess goes to global unallocated (null groupId)
      for (FriendGroupBalanceDTO debt : debtsToSettle) {
        BigDecimal owedAmount = debt.getBalance().abs().setScale(2, RoundingMode.HALF_UP);
        allocations.add(
            SettlementAllocation.builder().groupId(debt.getGroupId()).amount(owedAmount).build());
      }
      BigDecimal remainder = amount.subtract(totalOwed).setScale(2, RoundingMode.HALF_UP);
      if (remainder.compareTo(BigDecimal.ZERO) > 0) {
        allocations.add(SettlementAllocation.builder().groupId(null).amount(remainder).build());
      }
    } else {
      // Partial settlement: allocate pro-rata based on debt proportions
      BigDecimal accumulatedAllocated = BigDecimal.ZERO;
      for (int i = 0; i < debtsToSettle.size(); i++) {
        FriendGroupBalanceDTO debt = debtsToSettle.get(i);
        BigDecimal groupDebt = debt.getBalance().abs();

        BigDecimal groupAllocation;
        if (i == debtsToSettle.size() - 1) {
          // Last group takes the remaining amount to prevent penny rounding mismatch
          groupAllocation = amount.subtract(accumulatedAllocated).setScale(2, RoundingMode.HALF_UP);
        } else {
          groupAllocation =
              amount
                  .multiply(groupDebt)
                  .divide(totalOwed, 4, RoundingMode.HALF_UP)
                  .setScale(2, RoundingMode.HALF_UP);
          accumulatedAllocated = accumulatedAllocated.add(groupAllocation);
        }

        if (groupAllocation.compareTo(BigDecimal.ZERO) > 0) {
          allocations.add(
              SettlementAllocation.builder()
                  .groupId(debt.getGroupId())
                  .amount(groupAllocation)
                  .build());
        }
      }
    }

    return allocations;
  }
}
