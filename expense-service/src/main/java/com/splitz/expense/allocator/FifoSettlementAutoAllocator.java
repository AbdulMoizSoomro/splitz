package com.splitz.expense.allocator;

import com.splitz.expense.dto.FriendBalanceResponseDTO;
import com.splitz.expense.dto.FriendGroupBalanceDTO;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.service.BalanceService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class FifoSettlementAutoAllocator implements SettlementAutoAllocator {

  private final BalanceService balanceService;

  public FifoSettlementAutoAllocator(BalanceService balanceService) {
    this.balanceService = balanceService;
  }

  @Override
  public List<SettlementAllocation> allocate(Long payerId, Long payeeId, BigDecimal amount) {
    List<SettlementAllocation> allocations = new ArrayList<>();
    BigDecimal remainingAmount = amount;

    FriendBalanceResponseDTO balanceResponse =
        balanceService.getNetBalanceWithFriend(payerId, payeeId);

    // Filter to groups where payer owes payee (payer net balance is negative)
    List<FriendGroupBalanceDTO> debtsToSettle =
        balanceResponse.getGroupBalances().stream()
            .filter(gb -> gb.getBalance().compareTo(BigDecimal.ZERO) < 0)
            .collect(Collectors.toList());

    // Sort by groupId ascending (oldest group is created first)
    debtsToSettle.sort((a, b) -> a.getGroupId().compareTo(b.getGroupId()));

    for (FriendGroupBalanceDTO debt : debtsToSettle) {
      if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
        break;
      }
      BigDecimal owedAmount = debt.getBalance().abs();
      BigDecimal allocatedAmount = remainingAmount.min(owedAmount);

      allocations.add(
          SettlementAllocation.builder()
              .groupId(debt.getGroupId())
              .amount(allocatedAmount)
              .build());

      remainingAmount = remainingAmount.subtract(allocatedAmount);
    }

    if (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
      allocations.add(SettlementAllocation.builder().groupId(null).amount(remainingAmount).build());
    }

    return allocations;
  }
}
