package com.splitz.expense.service;

import com.splitz.expense.balance.DebtBalanceEngine;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.DebtSimplificationPlanDTO;
import com.splitz.expense.dto.SimplifiedDebtTransactionDTO;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.model.DebtSimplificationPlan;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupSimplificationSettings;
import com.splitz.expense.model.PlanStatus;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.GroupSimplificationSettingsRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the Smart Debt Reduction Engine (Wayfinder issue #63). Computes a read-only
 * Suggested Settlement Plan by gathering live net balances and governance opt-outs, then delegating
 * to the {@link GreedyDebtSimplifier}. Never mutates balances (decision #67).
 */
@Service
@RequiredArgsConstructor
public class DebtSimplificationPlanService {

  private final GroupRepository groupRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final ExpenseRepository expenseRepository;
  private final SettlementAllocationRepository settlementAllocationRepository;
  private final GroupSimplificationSettingsRepository settingsRepository;
  private final UserClient userClient;
  private final DebtBalanceEngine debtBalanceEngine;
  private final GreedyDebtSimplifier greedyDebtSimplifier;
  private final BalanceService balanceService;

  @Transactional(readOnly = true)
  public DebtSimplificationPlanDTO computePlan(Long groupId) {
    if (!groupRepository.existsById(groupId)) {
      throw new ResourceNotFoundException("Group not found with id: " + groupId);
    }

    GroupSimplificationSettings settings = resolveSettings(groupId);

    if (!settings.isSimplificationEnabled()) {
      return emptyPlan(groupId, settings);
    }

    List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
    List<Long> memberIds =
        members.stream().map(GroupMember::getUserId).collect(Collectors.toList());

    Map<Long, BigDecimal> netBalances;
    int originalTransactionCount;
    if (settings.getSimplificationScope() == SimplificationScope.CROSS_GROUP) {
      netBalances = computeCrossGroupBalances(memberIds);
      originalTransactionCount = 0;
    } else {
      List<Expense> expenses = expenseRepository.findByGroupId(groupId);
      List<SettlementAllocation> allocations =
          settlementAllocationRepository.findByGroupId(groupId);
      netBalances = debtBalanceEngine.calculateGroupBalances(memberIds, expenses, allocations);
      originalTransactionCount = expenses.size() + allocations.size();
    }

    Map<Long, String> usernames = resolveUsernames(memberIds);

    DebtSimplificationPlan plan =
        greedyDebtSimplifier.simplifyDebts(
            groupId, netBalances, settings.getOptOutUserIds(), usernames, originalTransactionCount);

    return toDTO(plan, settings);
  }

  private GroupSimplificationSettings resolveSettings(Long groupId) {
    return settingsRepository
        .findByGroupId(groupId)
        .orElseGet(
            () ->
                GroupSimplificationSettings.builder()
                    .groupId(groupId)
                    .simplificationEnabled(true)
                    .simplificationScope(SimplificationScope.INTRA_GROUP)
                    .optOutUserIds(new HashSet<>())
                    .build());
  }

  private Map<Long, BigDecimal> computeCrossGroupBalances(List<Long> memberIds) {
    Map<Long, BigDecimal> netBalances = new HashMap<>();
    for (Long memberId : memberIds) {
      BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
      List<GroupMember> memberships = groupMemberRepository.findByUserId(memberId);
      for (GroupMember membership : memberships) {
        Long gid = membership.getGroup().getId();
        total = total.add(balanceService.calculateUserBalanceInGroup(memberId, gid));
      }
      netBalances.put(memberId, total);
    }
    return netBalances;
  }

  private Map<Long, String> resolveUsernames(List<Long> memberIds) {
    List<UserResponse> users = userClient.getUsersByIds(memberIds);
    Map<Long, String> usernames = new HashMap<>();
    for (UserResponse user : users) {
      usernames.put(user.getId(), user.getUsername());
    }
    return usernames;
  }

  private static HashSet<Long> safeCopyOptOutIds(GroupSimplificationSettings settings) {
    return settings.getOptOutUserIds() != null
        ? new HashSet<>(settings.getOptOutUserIds())
        : new HashSet<>();
  }

  private DebtSimplificationPlanDTO emptyPlan(Long groupId, GroupSimplificationSettings settings) {
    return DebtSimplificationPlanDTO.builder()
        .groupId(groupId)
        .scope(settings.getSimplificationScope().name())
        .status(PlanStatus.PROPOSED.name())
        .simplificationEnabled(false)
        .originalTransactionCount(0)
        .simplifiedTransactionCount(0)
        .totalDebtVolume(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
        .optedOutUserIds(safeCopyOptOutIds(settings))
        .transactions(Collections.emptyList())
        .build();
  }

  private DebtSimplificationPlanDTO toDTO(
      DebtSimplificationPlan plan, GroupSimplificationSettings settings) {
    List<SimplifiedDebtTransactionDTO> transactionDTOs =
        plan.getTransactions().stream()
            .map(
                tx ->
                    SimplifiedDebtTransactionDTO.builder()
                        .fromUserId(tx.getFromUserId())
                        .fromUsername(tx.getFromUsername())
                        .toUserId(tx.getToUserId())
                        .toUsername(tx.getToUsername())
                        .amount(tx.getAmount())
                        .status(tx.getStatus().name())
                        .build())
            .collect(Collectors.toList());

    return DebtSimplificationPlanDTO.builder()
        .groupId(plan.getGroupId())
        .scope(settings.getSimplificationScope().name())
        .status(plan.getStatus().name())
        .simplificationEnabled(settings.isSimplificationEnabled())
        .originalTransactionCount(plan.getOriginalTransactionCount())
        .simplifiedTransactionCount(plan.getSimplifiedTransactionCount())
        .totalDebtVolume(plan.getTotalDebtVolume())
        .optedOutUserIds(safeCopyOptOutIds(settings))
        .transactions(transactionDTOs)
        .build();
  }
}
