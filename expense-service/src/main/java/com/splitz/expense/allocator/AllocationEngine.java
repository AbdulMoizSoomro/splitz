package com.splitz.expense.allocator;

import com.splitz.expense.dto.CreateSettlementRequest;
import com.splitz.expense.model.SettlementAllocation;
import java.math.BigDecimal;
import java.util.List;

/**
 * Deep module owning the resolution of a Payment's amount into a list of {@link
 * SettlementAllocation}s.
 *
 * <p>Hides the three-way branching (group-bound, explicit manual, auto-allocate) and the validation
 * rules (total must match payment amount) behind a single narrow interface. Callers (controllers,
 * services) supply the payment's context; this module decides how allocations are produced.
 */
public interface AllocationEngine {

  /**
   * Resolves a payment's context into validated {@link SettlementAllocation} entities.
   *
   * @param payerId the user making the payment
   * @param payeeId the user receiving the payment
   * @param amount the total payment amount
   * @param groupId optional group ID for group-bound payments (may be null)
   * @param explicitAllocations optional explicit allocation requests (may be null or empty)
   * @return a non-empty list of settlement allocations whose amounts sum to {@code amount}
   */
  List<SettlementAllocation> resolveAllocations(
      Long payerId,
      Long payeeId,
      BigDecimal amount,
      Long groupId,
      List<CreateSettlementRequest.Allocation> explicitAllocations);
}
