package com.splitz.expense.repository;

import java.math.BigDecimal;

/**
 * Row projection for GROUP BY aggregation queries keyed by (userId, groupId), e.g. total amount
 * paid or share owed per user per group.
 */
public interface UserGroupAggregate {

  Long getUserId();

  Long getGroupId();

  BigDecimal getTotal();
}
