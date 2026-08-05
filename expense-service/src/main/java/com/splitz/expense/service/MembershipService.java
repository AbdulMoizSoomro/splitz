package com.splitz.expense.service;

import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.AddMemberRequest;
import com.splitz.expense.dto.BulkAddMembersRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.UpdateMemberRoleRequest;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.governance.GroupGovernance;
import com.splitz.expense.mapper.GroupMapper;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deep Membership Engine that owns the GroupMember lifecycle, potential member resolution, and
 * enforces all governance and financial invariants (ADR-0002).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MembershipService implements GroupGovernance {

  private final GroupRepository groupRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final SettlementAllocationRepository settlementAllocationRepository;
  private final GroupMapper groupMapper;
  private final UserClient userClient;
  private final BalanceService balanceService;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  // --- Membership Lifecycle Operations ---

  public GroupDTO addMember(Long groupId, AddMemberRequest request, Long userId) {
    assertCanManageMembers(groupId, userId);
    Group group = getGroupWithMembers(groupId);

    if (groupMemberRepository.existsByGroupIdAndUserId(groupId, request.getUserId())) {
      throw new IllegalArgumentException("User is already a member of this group");
    }

    if (!userClient.existsById(request.getUserId())) {
      throw new ResourceNotFoundException("User not found with id: " + request.getUserId());
    }

    GroupRole role = Optional.ofNullable(request.getRole()).orElse(GroupRole.MEMBER);
    GroupMember member = GroupMember.builder().userId(request.getUserId()).role(role).build();
    group.addMember(member);

    Group saved = groupRepository.save(group);
    return groupMapper.toDTO(saved);
  }

  public GroupDTO bulkAddMembers(Long groupId, BulkAddMembersRequest request, Long userId) {
    if (request.getUserIds() != null && request.getUserIds().size() > 50) {
      throw new IllegalArgumentException("Maximum 50 users can be added at once");
    }

    assertCanManageMembers(groupId, userId);
    Group group = getGroupWithMembers(groupId);

    if (request.getUserIds() != null) {
      for (Long memberUserId : request.getUserIds()) {
        // Skip users already in the group
        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, memberUserId)) {
          continue;
        }
        if (!userClient.existsById(memberUserId)) {
          throw new ResourceNotFoundException("User not found with id: " + memberUserId);
        }
        GroupMember member =
            GroupMember.builder().userId(memberUserId).role(GroupRole.MEMBER).build();
        group.addMember(member);
      }
    }

    Group saved = groupRepository.save(group);
    return groupMapper.toDTO(saved);
  }

  @Transactional(readOnly = true)
  public List<UserResponse> getPotentialMembers(Long groupId, Long userId) {
    // 1. Get friends
    List<UserResponse> friends = userClient.getFriends(userId);
    Set<Long> friendIds = friends.stream().map(UserResponse::getId).collect(Collectors.toSet());

    // 2. Get all group IDs for user
    List<Long> userGroupIds =
        groupRepository.findDistinctByMembersUserIdAndActiveTrue(userId).stream()
            .map(Group::getId)
            .toList();

    // 3. Get all members in those groups (Potential Temp Friends)
    Set<Long> potentialIds =
        groupMemberRepository.findByGroupIdIn(userGroupIds).stream()
            .map(GroupMember::getUserId)
            .filter(id -> !id.equals(userId))
            .collect(Collectors.toSet());

    // 4. Combine and Filter
    // Those who are either friends OR shared group members, but NOT already in the target group
    Group targetGroup = getGroupWithMembers(groupId);
    Set<Long> existingMemberIds =
        targetGroup.getMembers().stream().map(GroupMember::getUserId).collect(Collectors.toSet());

    potentialIds.addAll(friendIds);
    potentialIds.removeAll(existingMemberIds);

    // 5. Fetch details for those not already in 'friends' (since we already have their details)
    List<Long> idsToFetch =
        potentialIds.stream().filter(id -> !friendIds.contains(id)).collect(Collectors.toList());

    List<UserResponse> sharedMembers = userClient.getUsersByIds(idsToFetch);

    List<UserResponse> results = new ArrayList<>(friends);
    // Filter friends to only those not in group
    results.removeIf(f -> existingMemberIds.contains(f.getId()));
    results.addAll(sharedMembers);

    return results;
  }

  public void removeMember(Long groupId, Long memberUserId, Long userId) {
    assertCanRemoveMember(groupId, userId, memberUserId);

    Group group = getGroupWithMembers(groupId);
    GroupMember member =
        groupMemberRepository
            .findByGroupIdAndUserId(groupId, memberUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found in this group"));

    group.removeMember(member);
    groupMemberRepository.delete(member);
  }

  public GroupDTO updateMemberRole(
      Long groupId, Long memberUserId, UpdateMemberRoleRequest request, Long userId) {
    assertCanChangeRole(groupId, userId, memberUserId, request.getRole());

    Group group = getGroupWithMembers(groupId);
    GroupMember member =
        groupMemberRepository
            .findByGroupIdAndUserId(groupId, memberUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found in this group"));

    member.setRole(request.getRole());
    return groupMapper.toDTO(groupRepository.save(group));
  }

  // --- Group Governance Invariant & Assertion Methods ---

  @Override
  @Transactional(readOnly = true)
  public void assertIsMember(Long groupId, Long userId) {
    if (splitzAuthorizer.isAdmin()) {
      getGroupWithMembers(groupId);
      return;
    }
    getGroupWithMembers(groupId);
    boolean isMember = groupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    if (!isMember) {
      throw new UnauthorizedException("You are not a member of this group");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public void assertCanManageMembers(Long groupId, Long actorUserId) {
    Group group = getGroupWithMembers(groupId);
    if (splitzAuthorizer.isAdmin()) {
      return;
    }
    GroupMember member =
        groupMemberRepository
            .findByGroupIdAndUserId(groupId, actorUserId)
            .orElseThrow(() -> new UnauthorizedException("You are not a member of this group"));

    if (member.getRole() == GroupRole.ADMIN || actorUserId.equals(group.getCreatedBy())) {
      return;
    }

    if (!group.isAllowMembersToManageMembers()) {
      throw new UnauthorizedException("You do not have permission to manage members in this group");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public void assertCanEditExpense(Long groupId, Long actorUserId, Long expenseCreatorUserId) {
    Group group = getGroupWithMembers(groupId);
    if (splitzAuthorizer.isAdmin()) {
      return;
    }
    GroupMember member =
        groupMemberRepository
            .findByGroupIdAndUserId(groupId, actorUserId)
            .orElseThrow(() -> new UnauthorizedException("You are not a member of this group"));

    if (actorUserId.equals(expenseCreatorUserId)) {
      return;
    }

    if (member.getRole() == GroupRole.ADMIN || actorUserId.equals(group.getCreatedBy())) {
      return;
    }

    if (!group.isAllowMembersToEditExpenses()) {
      throw new UnauthorizedException("You do not have permission to edit expenses in this group");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public void assertCanChangeRole(
      Long groupId, Long actorUserId, Long targetUserId, GroupRole newRole) {
    Group group = getGroupWithMembers(groupId);

    GroupMember targetMember =
        groupMemberRepository
            .findByGroupIdAndUserId(groupId, targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found in this group"));

    if (targetUserId.equals(group.getCreatedBy())) {
      throw new UnauthorizedException("The group owner role cannot be modified");
    }

    if (splitzAuthorizer.isAdmin()) {
      return;
    }

    GroupMember actorMember =
        groupMemberRepository
            .findByGroupIdAndUserId(groupId, actorUserId)
            .orElseThrow(() -> new UnauthorizedException("You are not a member of this group"));

    if (actorMember.getRole() != GroupRole.ADMIN) {
      throw new UnauthorizedException("Only admins can perform this action");
    }

    if (targetMember.getRole() == GroupRole.ADMIN
        && newRole == GroupRole.MEMBER
        && !actorUserId.equals(group.getCreatedBy())) {
      throw new UnauthorizedException("Only the group owner can demote another admin");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public void assertCanRemoveMember(Long groupId, Long actorUserId, Long targetUserId) {
    Group group = getGroupWithMembers(groupId);

    // Target protection: The target must be a member
    groupMemberRepository
        .findByGroupIdAndUserId(groupId, targetUserId)
        .orElseThrow(() -> new ResourceNotFoundException("Member not found in this group"));

    // Owner protection
    if (targetUserId.equals(group.getCreatedBy())) {
      throw new UnauthorizedException("The group owner cannot be removed from the group");
    }

    // Bypass check for Global Admin
    if (!splitzAuthorizer.isAdmin()) {
      // Actor membership check
      GroupMember actorMember =
          groupMemberRepository
              .findByGroupIdAndUserId(groupId, actorUserId)
              .orElseThrow(() -> new UnauthorizedException("You are not a member of this group"));

      // Admin role check when removing someone else
      if (!actorUserId.equals(targetUserId)) {
        if (actorMember.getRole() != GroupRole.ADMIN) {
          throw new UnauthorizedException("Only admins can perform this action");
        }
      }
    }

    // Settled Membership Invariant check
    BigDecimal balance = balanceService.calculateUserBalanceInGroup(targetUserId, groupId);
    if (balance != null && balance.compareTo(BigDecimal.ZERO) != 0) {
      throw new IllegalStateException("Cannot remove member with non-zero balance");
    }

    boolean hasActive =
        settlementAllocationRepository.hasActiveSettlementsForUserInGroup(
            targetUserId, groupId, List.of(SettlementStatus.PENDING, SettlementStatus.MARKED_PAID));
    if (hasActive) {
      throw new IllegalStateException("Cannot remove member with active settlements");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public void assertCanManageGroup(Long groupId, Long actorUserId) {
    if (splitzAuthorizer.isAdmin()) {
      getGroupWithMembers(groupId);
      return;
    }
    getGroupWithMembers(groupId);
    GroupMember member =
        groupMemberRepository
            .findByGroupIdAndUserId(groupId, actorUserId)
            .orElseThrow(() -> new UnauthorizedException("You are not a member of this group"));

    if (member.getRole() != GroupRole.ADMIN) {
      throw new UnauthorizedException("Only admins can perform this action");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isMember(Long groupId, Long userId) {
    try {
      if (!groupRepository.existsById(groupId)) {
        return false;
      }
      return groupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    } catch (Exception e) {
      return false;
    }
  }

  private Group getGroupWithMembers(Long groupId) {
    return groupRepository
        .findById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
  }
}
