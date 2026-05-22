package com.splitz.expense.allocator;

import com.splitz.expense.model.SettlementAllocation;
import java.math.BigDecimal;
import java.util.List;

public interface SettlementAutoAllocator {
  List<SettlementAllocation> allocate(Long payerId, Long payeeId, BigDecimal amount);
}
