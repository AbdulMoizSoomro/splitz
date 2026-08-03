package com.splitz.expense.service;

import com.splitz.expense.balancesource.NetBalanceResult;
import com.splitz.expense.balancesource.NetBalanceSource;
import com.splitz.expense.balancesource.NetBalanceSourceRegistry;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.DebtSimplificationPlanDTO;
import com.splitz.expense.dto.SimplifiedDebtTransactionDTO;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.model.DebtSimplificationPlan;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupSimplificationSettings;
import com.splitz.expense.model.PlanStatus;
import com.splitz.expense.netting.DebtNettingEngine;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
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
 * Suggested Settlement Plan by resolving the group's simplification settings, delegating balance
 * sourcing to the {@link NetBalanceSource} adapter selected for the scope, and delegating netting
 * to the deep {@link DebtNettingEngine}. Never mutates balances (decision #67). Each scope's
 * balance-sourcing algorithm lives in its own adapter (see the {@code balancesource} package)
 * rather than inside a branch of this class.
 */
@Service
@RequiredArgsConstructor
public class DebtSimplificationPlanService {

  private final GroupRepository groupRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final GroupSimplificationSettingsService settingsService;
  private final UserClient userClient;
  private final DebtNettingEngine debtNettingEngine;
  private final NetBalanceSourceRegistry netBalanceSourceRegistry;

  @Transactional(readOnly = true)
  public DebtSimplificationPlanDTO computePlan(Long groupId) {
    if (!groupRepository.existsById(groupId)) {
      throw new ResourceNotFoundException("Group not found with id: " + groupId);
    }

    GroupSimplificationSettings settings = settingsService.readSettings(groupId);

    if (!settings.isSimplificationEnabled()) {
      return emptyPlan(groupId, settings);
    }

    List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
    List<Long> memberIds =
        members.stream().map(GroupMember::getUserId).collect(Collectors.toList());

    NetBalanceSource source = netBalanceSourceRegistry.forScope(settings.getSimplificationScope());
    NetBalanceResult balanceResult = source.resolve(groupId, memberIds);

    Map<Long, String> usernames = resolveUsernames(memberIds);

    DebtSimplificationPlan plan =
        debtNettingEngine.simplifyDebts(
            groupId,
            balanceResult.getNetBalances(),
            settings.getOptOutUserIds(),
            usernames,
            balanceResult.getOriginalTransactionCount());

    return toDTO(plan, settings);
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
