package com.splitz.expense.balancesource;

import com.splitz.expense.balance.DebtBalanceEngine;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.repository.GroupMemberRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Balance-source adapter for the {@link SimplificationScope#CROSS_GROUP} scope: aggregates a single
 * member's net balances across all of their group memberships. Loads every member's memberships in
 * one {@link GroupMemberRepository#findByUserIdIn} lookup, evaluates all per-group balances in a
 * single batch {@link DebtBalanceEngine#calculateBalancesInGroups} round trip, and sums them per
 * member. Owns the "walk a member's memberships and sum per-group balances" algorithm — its
 * repositories and cost profile — in one place instead of inside the plan orchestrator's scope
 * branch.
 */
@Component
public class CrossGroupNetBalanceSource implements NetBalanceSource {

  private final GroupMemberRepository groupMemberRepository;
  private final DebtBalanceEngine debtBalanceEngine;

  public CrossGroupNetBalanceSource(
      GroupMemberRepository groupMemberRepository, DebtBalanceEngine debtBalanceEngine) {
    this.groupMemberRepository = groupMemberRepository;
    this.debtBalanceEngine = debtBalanceEngine;
  }

  @Override
  public SimplificationScope getSupportedScope() {
    return SimplificationScope.CROSS_GROUP;
  }

  @Override
  public NetBalanceResult resolve(Long groupId, List<Long> memberIds) {
    List<GroupMember> memberships = groupMemberRepository.findByUserIdIn(memberIds);

    Map<Long, Set<Long>> groupIdsByUser =
        memberships.stream()
            .collect(
                Collectors.groupingBy(
                    GroupMember::getUserId,
                    Collectors.mapping(
                        membership -> membership.getGroup().getId(), Collectors.toSet())));
    Set<Long> allGroupIds =
        groupIdsByUser.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());

    Map<Long, BigDecimal> netBalances = new HashMap<>();
    if (allGroupIds.isEmpty()) {
      for (Long memberId : memberIds) {
        netBalances.put(memberId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
      }
      return NetBalanceResult.builder()
          .netBalances(Collections.unmodifiableMap(netBalances))
          .originalTransactionCount(0)
          .build();
    }

    Map<Long, Map<Long, BigDecimal>> balances =
        debtBalanceEngine.calculateBalancesInGroups(memberIds, allGroupIds);

    for (Long memberId : memberIds) {
      BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
      Map<Long, BigDecimal> byGroup = balances.get(memberId);
      if (byGroup != null) {
        for (Long userGroupId : groupIdsByUser.getOrDefault(memberId, Collections.emptySet())) {
          total = total.add(byGroup.getOrDefault(userGroupId, BigDecimal.ZERO));
        }
      }
      netBalances.put(memberId, total);
    }
    return NetBalanceResult.builder()
        .netBalances(Collections.unmodifiableMap(netBalances))
        .originalTransactionCount(0)
        .build();
  }
}
