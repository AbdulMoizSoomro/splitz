package com.splitz.expense.netting;

import com.splitz.expense.balancesource.NetBalanceResult;
import com.splitz.expense.dto.DebtDTO;
import com.splitz.expense.model.DebtSimplificationPlan;
import com.splitz.expense.repository.LedgerRepository;
import com.splitz.expense.service.UserSimplificationPreferenceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps opted-out members' original debts outside the balances eligible for netting. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DebtProjectionEngine {

  private final UserSimplificationPreferenceService preferenceService;
  private final LedgerRepository ledgerRepository;
  private final DebtNettingEngine debtNettingEngine;

  public Projection project(
      Long groupId,
      List<Long> memberIds,
      Set<Long> groupOptOutUserIds,
      NetBalanceResult balances,
      Map<Long, String> usernames) {
    Set<Long> effectiveOptOuts =
        preferenceService.effectiveOptOutUserIds(groupOptOutUserIds, memberIds);
    Map<Long, BigDecimal> eligibleBalances = new HashMap<>(balances.getNetBalances());
    List<DebtDTO> preservedDebts = new ArrayList<>();
    List<Long> sortedMembers = memberIds.stream().distinct().sorted().toList();

    for (int i = 0; i < sortedMembers.size(); i++) {
      Long userA = sortedMembers.get(i);
      for (int j = i + 1; j < sortedMembers.size(); j++) {
        Long userB = sortedMembers.get(j);
        if (!effectiveOptOuts.contains(userA) && !effectiveOptOuts.contains(userB)) {
          continue;
        }
        Set<Long> groupsA = balances.getGroupIdsByUser().getOrDefault(userA, Set.of());
        Set<Long> groupsB = balances.getGroupIdsByUser().getOrDefault(userB, Set.of());
        Set<Long> pairGroups = new HashSet<>(groupsA);
        pairGroups.addAll(groupsB);
        BigDecimal pairwiseBalance = BigDecimal.ZERO;
        for (Long balanceGroupId : pairGroups) {
          BigDecimal groupBalance =
              ledgerRepository.calculatePairwiseBalanceInGroup(userA, userB, balanceGroupId);
          pairwiseBalance = pairwiseBalance.add(groupBalance);
          // Excluding only a member's net balance still permits debt to flow through them.
          // Remove protected contributions only where the source included that member's group.
          if (groupsA.contains(balanceGroupId)) {
            eligibleBalances.computeIfPresent(
                userA, (id, balance) -> balance.subtract(groupBalance));
          }
          if (groupsB.contains(balanceGroupId)) {
            eligibleBalances.computeIfPresent(userB, (id, balance) -> balance.add(groupBalance));
          }
        }
        pairwiseBalance = pairwiseBalance.setScale(2, RoundingMode.HALF_UP);
        if (pairwiseBalance.signum() == 0) {
          continue;
        }

        Long from = pairwiseBalance.signum() > 0 ? userB : userA;
        Long to = pairwiseBalance.signum() > 0 ? userA : userB;
        preservedDebts.add(
            DebtDTO.builder()
                .from(from)
                .fromUsername(usernames.get(from))
                .to(to)
                .toUsername(usernames.get(to))
                .amount(pairwiseBalance.abs())
                .build());
      }
    }

    DebtSimplificationPlan plan =
        debtNettingEngine.simplifyDebts(
            groupId,
            eligibleBalances,
            effectiveOptOuts,
            usernames,
            balances.getOriginalTransactionCount());
    return new Projection(plan, List.copyOf(preservedDebts), Set.copyOf(effectiveOptOuts));
  }

  public record Projection(
      DebtSimplificationPlan plan, List<DebtDTO> preservedDebts, Set<Long> optedOutUserIds) {

    public List<DebtDTO> debts() {
      List<DebtDTO> debts = new ArrayList<>(preservedDebts);
      if (plan != null) {
        debts.addAll(plan.toDebtDTOs());
      }
      return debts;
    }
  }
}
