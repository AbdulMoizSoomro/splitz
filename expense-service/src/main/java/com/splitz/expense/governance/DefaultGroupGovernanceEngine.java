package com.splitz.expense.governance;

import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import com.splitz.expense.service.BalanceService;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deep GroupGovernanceEngine module that encapsulates identity resolution from
 * SharedSecurityAuthorizer, domain role permissions, and financial invariants (Settled Membership,
 * Ownership, Peer Authority, Group Governance Settings).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultGroupGovernanceEngine implements GroupGovernance {

  private final GroupRepository groupRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final SettlementAllocationRepository settlementAllocationRepository;
  private final BalanceService balanceService;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  @Override
  public void assertIsMember(Long groupId, Long userId) {
    getGroupWithMembers(groupId);
    if (splitzAuthorizer.isAdmin()) {
      return;
    }
    boolean isMember = groupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    if (!isMember) {
      throw new UnauthorizedException("You are not a member of this group");
    }
  }

  @Override
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
