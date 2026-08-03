package com.splitz.expense.allocator;

import com.splitz.expense.model.SettlementAllocation;
import java.math.BigDecimal;
import java.util.List;

/**
 * Decides how a Payment's amount becomes a list of {@link SettlementAllocation}s across groups.
 *
 * <p>The interface is deep: it accepts a resolved {@link DebtPosition} (the payer's eligible group
 * debts) plus the payment amount, and hides the distribution strategy, rounding, and the
 * "remainder/over-payment goes to the global (null) allocation" rule. Eligible-debt discovery is
 * owned by {@link DebtPositionResolver}, so allocators are pure functions of their inputs.
 */
public interface SettlementAutoAllocator {

  List<SettlementAllocation> allocate(DebtPosition position, BigDecimal amount);
}
