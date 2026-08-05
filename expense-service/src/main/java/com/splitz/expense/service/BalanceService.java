package com.splitz.expense.service;

import com.splitz.expense.balance.DebtBalanceEngine;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.BalanceDTO;
import com.splitz.expense.dto.DebtDTO;
import com.splitz.expense.dto.FriendBalanceResponseDTO;
import com.splitz.expense.dto.FriendGroupBalanceDTO;
import com.splitz.expense.dto.GroupBalanceResponseDTO;
import com.splitz.expense.dto.UserBalanceResponseDTO;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.model.DebtSimplificationPlan;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.netting.DebtNettingEngine;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BalanceService {

  private final ExpenseRepository expenseRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final GroupRepository groupRepository;
  private final SettlementAllocationRepository settlementAllocationRepository;
  private final UserClient userClient;
  private final DebtBalanceEngine debtBalanceEngine;
  private final DebtNettingEngine debtNettingEngine;
  private final DebtPlanDebtDTOAdapter debtPlanDebtDTOAdapter;

  @Transactional(readOnly = true)
  public FriendBalanceResponseDTO getNetBalanceWithFriend(Long userId, Long friendId) {
    Set<Long> userGroupIds =
        groupMemberRepository.findByUserId(userId).stream()
            .map(gm -> gm.getGroup().getId())
            .collect(Collectors.toSet());
    Set<Long> friendGroupIds =
        groupMemberRepository.findByUserId(friendId).stream()
            .map(gm -> gm.getGroup().getId())
            .collect(Collectors.toSet());

    userGroupIds.retainAll(friendGroupIds);

    BigDecimal netBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    List<FriendGroupBalanceDTO> groupBalances = new ArrayList<>();

    if (!userGroupIds.isEmpty()) {
      List<Group> sharedGroups = groupRepository.findAllById(userGroupIds);
      Map<Long, String> groupNames =
          sharedGroups.stream().collect(Collectors.toMap(Group::getId, Group::getName));

      for (Long groupId : userGroupIds) {
        List<Expense> expenses = expenseRepository.findByGroupId(groupId);
        List<SettlementAllocation> allocations =
            settlementAllocationRepository.findByGroupId(groupId);
        BigDecimal groupNetBalance =
            debtBalanceEngine.calculateNetBalanceInGroup(userId, friendId, expenses, allocations);

        // Include all shared groups (even with zero balance) so the friend detail
        // page can display them. The settlement modal already filters by non-zero
        // balance for allocation purposes.
        groupBalances.add(
            FriendGroupBalanceDTO.builder()
                .groupId(groupId)
                .groupName(groupNames.get(groupId))
                .balance(groupNetBalance)
                .build());
        netBalance = netBalance.add(groupNetBalance);
      }
    }

    // 3. Global Friendship Settlements (no group)
    BigDecimal globalSettled = debtBalanceEngine.calculateGlobalSettlementBalance(userId, friendId);
    netBalance = netBalance.add(globalSettled);

    return FriendBalanceResponseDTO.builder()
        .userId(userId)
        .friendId(friendId)
        .netBalance(netBalance)
        .groupBalances(groupBalances)
        .build();
  }

  @Transactional(readOnly = true)
  public GroupBalanceResponseDTO getGroupBalances(Long groupId) {
    if (!groupRepository.existsById(groupId)) {
      throw new ResourceNotFoundException("Group not found with id: " + groupId);
    }

    List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
    List<Expense> expenses = expenseRepository.findByGroupId(groupId);
    List<SettlementAllocation> allocations = settlementAllocationRepository.findByGroupId(groupId);

    List<Long> memberIds =
        members.stream().map(GroupMember::getUserId).collect(Collectors.toList());
    Map<Long, BigDecimal> balances =
        debtBalanceEngine.calculateGroupBalances(memberIds, expenses, allocations);

    List<Long> userIds = new ArrayList<>(balances.keySet());
    List<UserResponse> userResponses = userClient.getUsersByIds(userIds);
    Map<Long, UserResponse> userMap = new HashMap<>();
    userResponses.forEach(u -> userMap.put(u.getId(), u));

    List<BalanceDTO> balanceDTOs = new ArrayList<>();
    balances.forEach(
        (userId, balance) -> {
          UserResponse user = userMap.get(userId);
          balanceDTOs.add(
              BalanceDTO.builder()
                  .userId(userId)
                  .username(user != null ? user.getUsername() : null)
                  .email(user != null ? user.getEmail() : null)
                  .firstName(user != null ? user.getFirstName() : null)
                  .lastName(user != null ? user.getLastName() : null)
                  .balance(balance)
                  .build());
        });

    Map<Long, String> usernames =
        userMap.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().getUsername() : null));
    DebtSimplificationPlan plan =
        debtNettingEngine.simplifyDebts(groupId, balances, Collections.emptySet(), usernames, 0);
    List<DebtDTO> simplifiedDebts = debtPlanDebtDTOAdapter.toDebtDtos(plan);

    return GroupBalanceResponseDTO.builder()
        .groupId(groupId)
        .balances(balanceDTOs)
        .simplifiedDebts(simplifiedDebts)
        .build();
  }

  @Transactional(readOnly = true)
  public UserBalanceResponseDTO getUserBalances(Long userId) {
    List<GroupMember> memberships = groupMemberRepository.findByUserId(userId);
    List<UserBalanceResponseDTO.GroupBalanceDTO> groupBalances = new ArrayList<>();

    BigDecimal totalBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    for (GroupMember membership : memberships) {
      Long groupId = membership.getGroup().getId();
      String groupName = membership.getGroup().getName();

      BigDecimal userBalance = calculateUserBalanceInGroup(userId, groupId);

      groupBalances.add(
          UserBalanceResponseDTO.GroupBalanceDTO.builder()
              .groupId(groupId)
              .groupName(groupName)
              .balance(userBalance)
              .build());

      totalBalance = totalBalance.add(userBalance);
    }

    BigDecimal globalBalance = debtBalanceEngine.calculateUserGlobalSettlementBalance(userId);
    totalBalance = totalBalance.add(globalBalance);

    UserResponse user = userClient.getUserById(userId).orElse(null);

    return UserBalanceResponseDTO.builder()
        .userId(userId)
        .username(user != null ? user.getUsername() : null)
        .email(user != null ? user.getEmail() : null)
        .totalBalance(totalBalance)
        .groupBalances(groupBalances)
        .build();
  }

  public BigDecimal calculateUserBalanceInGroup(Long userId, Long groupId) {
    return debtBalanceEngine.calculateUserBalanceInGroup(userId, groupId);
  }
}
