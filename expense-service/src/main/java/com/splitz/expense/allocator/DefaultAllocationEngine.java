package com.splitz.expense.allocator;

import com.splitz.expense.dto.CreateFriendshipSettlementRequest;
import com.splitz.expense.model.SettlementAllocation;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultAllocationEngine implements AllocationEngine {

  private final DebtPositionResolver debtPositionResolver;
  private final SettlementAutoAllocator settlementAutoAllocator;

  @Override
  public List<SettlementAllocation> resolveAllocations(
      Long payerId,
      Long payeeId,
      BigDecimal amount,
      Long groupId,
      List<CreateFriendshipSettlementRequest.Allocation> explicitAllocations) {
    if (groupId != null) {
      return List.of(SettlementAllocation.builder().groupId(groupId).amount(amount).build());
    }
    if (explicitAllocations != null && !explicitAllocations.isEmpty()) {
      BigDecimal totalAllocated =
          explicitAllocations.stream()
              .map(CreateFriendshipSettlementRequest.Allocation::getAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      if (totalAllocated.compareTo(amount) != 0) {
        throw new IllegalArgumentException(
            "Total allocated amount ("
                + totalAllocated
                + ") must match payment amount ("
                + amount
                + ")");
      }
      return explicitAllocations.stream()
          .map(
              a ->
                  SettlementAllocation.builder()
                      .groupId(a.getGroupId())
                      .amount(a.getAmount())
                      .build())
          .toList();
    }
    DebtPosition position = debtPositionResolver.resolve(payerId, payeeId);
    return settlementAutoAllocator.allocate(position, amount);
  }
}
