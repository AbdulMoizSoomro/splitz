package com.splitz.expense.balancesource;

import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.service.BalanceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Balance-source adapter for the {@link SimplificationScope#CROSS_GROUP} scope: aggregates a single
 * member's net balances across all of their group memberships by walking each member's memberships
 * and calling {@link BalanceService#calculateUserBalanceInGroup} per group. Owns the "walk a
 * member's memberships and sum per-group balances" algorithm — its repositories and per-group cost
 * profile — in one place instead of inside the plan orchestrator's scope branch.
 */
@Component
public class CrossGroupNetBalanceSource implements NetBalanceSource {

  private final GroupMemberRepository groupMemberRepository;
  private final BalanceService balanceService;

  public CrossGroupNetBalanceSource(
      GroupMemberRepository groupMemberRepository, BalanceService balanceService) {
    this.groupMemberRepository = groupMemberRepository;
    this.balanceService = balanceService;
  }

  @Override
  public SimplificationScope getSupportedScope() {
    return SimplificationScope.CROSS_GROUP;
  }

  @Override
  public NetBalanceResult resolve(Long groupId, List<Long> memberIds) {
    Map<Long, BigDecimal> netBalances = new HashMap<>();
    for (Long memberId : memberIds) {
      BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
      List<GroupMember> memberships = groupMemberRepository.findByUserId(memberId);
      for (GroupMember membership : memberships) {
        total =
            total.add(
                balanceService.calculateUserBalanceInGroup(
                    memberId, membership.getGroup().getId()));
      }
      netBalances.put(memberId, total);
    }
    return NetBalanceResult.builder()
        .netBalances(Collections.unmodifiableMap(netBalances))
        .originalTransactionCount(0)
        .build();
  }
}
