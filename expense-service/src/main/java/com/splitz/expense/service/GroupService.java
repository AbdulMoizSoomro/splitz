package com.splitz.expense.service;

import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.AddMemberRequest;
import com.splitz.expense.dto.BulkAddMembersRequest;
import com.splitz.expense.dto.CreateGroupRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.UpdateGroupRequest;
import com.splitz.expense.dto.UpdateMemberRoleRequest;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.mapper.GroupMapper;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupService {

  private final GroupRepository groupRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final GroupMapper groupMapper;
  private final UserClient userClient;

  public GroupDTO createGroup(CreateGroupRequest request, Long currentUserId) {
    Group group =
        Group.builder()
            .name(request.getName())
            .description(request.getDescription())
            .imageUrl(request.getImageUrl())
            .createdBy(currentUserId)
            .active(true)
            .build();

    GroupMember creatorMembership =
        GroupMember.builder().userId(currentUserId).role(GroupRole.ADMIN).build();
    group.addMember(creatorMembership);

    if (request.getMemberUserIds() != null) {
      for (Long memberUserId : request.getMemberUserIds()) {
        if (!memberUserId.equals(currentUserId)) {
          if (!userClient.existsById(memberUserId)) {
            throw new ResourceNotFoundException("User not found with id: " + memberUserId);
          }
          group.addMember(
              GroupMember.builder().userId(memberUserId).role(GroupRole.MEMBER).build());
        }
      }
    }

    Group saved = groupRepository.save(group);
    return groupMapper.toDTO(saved);
  }

  @Transactional(readOnly = true)
  public List<GroupDTO> getGroupsForUser(Long userId) {
    return groupRepository.findDistinctByMembersUserIdAndActiveTrue(userId).stream()
        .map(groupMapper::toDTO)
        .toList();
  }

  @Transactional(readOnly = true)
  public GroupDTO getGroup(Long groupId, Long userId) {
    Group group = getGroupWithMembers(groupId);
    requireMembership(group, userId);
    return groupMapper.toDTO(group);
  }

  public GroupDTO updateGroup(Long groupId, UpdateGroupRequest request, Long userId) {
    Group group = getGroupWithMembers(groupId);
    requireAdmin(group, userId);

    if (request.getName() != null) {
      group.setName(request.getName());
    }
    if (request.getDescription() != null) {
      group.setDescription(request.getDescription());
    }
    if (request.getImageUrl() != null) {
      group.setImageUrl(request.getImageUrl());
    }
    if (request.getAllowMembersToManageMembers() != null) {
      group.setAllowMembersToManageMembers(request.getAllowMembersToManageMembers());
    }
    if (request.getAllowMembersToEditExpenses() != null) {
      group.setAllowMembersToEditExpenses(request.getAllowMembersToEditExpenses());
    }
    return groupMapper.toDTO(groupRepository.save(group));
  }

  public void deleteGroup(Long groupId, Long userId) {
    Group group = getGroupWithMembers(groupId);
    requireAdmin(group, userId);
    group.setActive(false);
    groupRepository.save(group);
  }

  public GroupDTO addMember(Long groupId, AddMemberRequest request, Long userId) {
    Group group = getGroupWithMembers(groupId);
    requireCanManageMembers(group, userId);

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

    Group group = getGroupWithMembers(groupId);
    requireCanManageMembers(group, userId);

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
    Group group = getGroupWithMembers(groupId);

    // Allow self-removal (leaving) or require admin role for removing others
    if (!memberUserId.equals(userId)) {
      requireAdmin(group, userId);
    } else {
      requireMembership(group, userId);
    }

    GroupMember member =
        groupMemberRepository
            .findByGroupIdAndUserId(groupId, memberUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found in this group"));

    // Owner protection
    if (member.getUserId().equals(group.getCreatedBy())) {
      throw new UnauthorizedException("The group owner cannot be removed from the group");
    }

    group.removeMember(member);
    groupMemberRepository.delete(member);
  }

  public GroupDTO updateMemberRole(
      Long groupId, Long memberUserId, UpdateMemberRoleRequest request, Long userId) {
    Group group = getGroupWithMembers(groupId);
    requireAdmin(group, userId);

    GroupMember member =
        groupMemberRepository
            .findByGroupIdAndUserId(groupId, memberUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found in this group"));

    // Owner protection: The owner cannot be demoted or have their role changed by others.
    if (member.getUserId().equals(group.getCreatedBy())) {
      throw new UnauthorizedException("The group owner role cannot be modified");
    }

    // Admin wars protection: Only owner can demote an Admin (unless self-demoting)
    if (member.getRole() == GroupRole.ADMIN
        && request.getRole() == GroupRole.MEMBER
        && !userId.equals(group.getCreatedBy())
        && !userId.equals(memberUserId)) {
      throw new UnauthorizedException("Only the group owner can demote another admin");
    }

    member.setRole(request.getRole());
    return groupMapper.toDTO(groupRepository.save(group));
  }

  private Group getGroupWithMembers(Long groupId) {
    return groupRepository
        .findById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
  }

  private void requireMembership(Group group, Long userId) {
    boolean isMember =
        group.getMembers().stream().anyMatch(member -> member.getUserId().equals(userId));
    if (!isMember) {
      throw new UnauthorizedException("You are not a member of this group");
    }
  }

  private void requireAdmin(Group group, Long userId) {
    GroupMember membership =
        group.getMembers().stream()
            .filter(member -> member.getUserId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new UnauthorizedException("You are not a member of this group"));
    if (membership.getRole() != GroupRole.ADMIN) {
      throw new UnauthorizedException("Only admins can perform this action");
    }
  }

  private void requireCanManageMembers(Group group, Long userId) {
    GroupMember membership =
        group.getMembers().stream()
            .filter(member -> member.getUserId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new UnauthorizedException("You are not a member of this group"));

    if (membership.getRole() == GroupRole.ADMIN || group.getCreatedBy().equals(userId)) {
      return;
    }

    if (!group.isAllowMembersToManageMembers()) {
      throw new UnauthorizedException("Only admins can manage members in this group");
    }
  }

  public boolean canManageExpenses(Group group, Long userId, Long payerId) {
    GroupMember membership =
        groupMemberRepository.findByGroupIdAndUserId(group.getId(), userId).orElse(null);

    if (membership == null) {
      return false;
    }

    // 1. Admin can always manage
    if (membership.getRole() == GroupRole.ADMIN) {
      return true;
    }

    // 2. Payer (creator of expense) can always manage
    if (userId.equals(payerId)) {
      return true;
    }

    // 3. Any member can manage if group setting is enabled
    return group.isAllowMembersToEditExpenses();
  }
}
