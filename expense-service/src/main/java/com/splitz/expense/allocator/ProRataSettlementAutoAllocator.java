package com.splitz.expense.allocator;

import com.splitz.expense.model.SettlementAllocation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Pro-Rata Settlement Allocator (Wayfinder Issue #71 — Slice 4). Distributes a Payment's amount
 * proportionally across the groups in a {@link DebtPosition} based on outstanding debt magnitudes.
 *
 * <p>A pure distribution function: it holds no BalanceService dependency and consumes a resolved
 * {@link DebtPosition}, so it is trivially testable through its interface. Eligible-debt discovery
 * lives in {@link DebtPositionResolver}.
 */
@Component
public class ProRataSettlementAutoAllocator implements SettlementAutoAllocator {

  @Override
  public List<SettlementAllocation> allocate(DebtPosition position, BigDecimal amount) {
    List<SettlementAllocation> allocations = new ArrayList<>();

    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      return allocations;
    }

    List<GroupDebt> debts = position != null ? position.getDebts() : null;

    if (debts == null || debts.isEmpty()) {
      allocations.add(SettlementAllocation.builder().groupId(null).amount(amount).build());
      return allocations;
    }

    BigDecimal totalOwed =
        debts.stream().map(GroupDebt::getOwedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalOwed.compareTo(BigDecimal.ZERO) == 0) {
      allocations.add(SettlementAllocation.builder().groupId(null).amount(amount).build());
      return allocations;
    }

    if (amount.compareTo(totalOwed) >= 0) {
      // Full settlement of all debts; excess goes to global unallocated (null groupId)
      for (GroupDebt debt : debts) {
        BigDecimal owedAmount = debt.getOwedAmount().setScale(2, RoundingMode.HALF_UP);
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
      for (int i = 0; i < debts.size(); i++) {
        GroupDebt debt = debts.get(i);
        BigDecimal groupDebt = debt.getOwedAmount();

        BigDecimal groupAllocation;
        if (i == debts.size() - 1) {
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
