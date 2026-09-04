package com.splitz.expense.service;

import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.AddMemberRequest;
import com.splitz.expense.dto.BulkAddMembersRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.UpdateMemberRoleRequest;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.governance.GroupGovernance;
import com.splitz.expense.mapper.GroupMapper;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deep Membership Engine that owns the GroupMember state lifecycle and potential member resolution
 * (ADR-0002).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MembershipService {

  private final GroupRepository groupRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final GroupMapper groupMapper;
  private final UserClient userClient;
  private final GroupGovernance groupGovernance;

  // --- Membership Lifecycle Operations ---

  public GroupDTO addMember(Long groupId, AddMemberRequest request, Long userId) {
    groupGovernance.assertCanManageMembers(groupId, userId);
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

    groupGovernance.assertCanManageMembers(groupId, userId);
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

  public GroupDTO initializeGroupMembers(Long groupId, Long creatorId, List<Long> memberIds) {
    if (memberIds != null && memberIds.size() > 50) {
      throw new IllegalArgumentException("Maximum 50 users can be added at once");
    }

    Group group = getGroupWithMembers(groupId);

    GroupMember creatorMembership =
        GroupMember.builder().userId(creatorId).role(GroupRole.ADMIN).build();
    group.addMember(creatorMembership);

    if (memberIds != null) {
      Set<Long> uniqueMemberIds = new LinkedHashSet<>(memberIds);
      uniqueMemberIds.remove(creatorId);

      for (Long memberUserId : uniqueMemberIds) {
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
    groupGovernance.assertCanRemoveMember(groupId, userId, memberUserId);

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
    groupGovernance.assertCanChangeRole(groupId, userId, memberUserId, request.getRole());

    Group group = getGroupWithMembers(groupId);
    GroupMember member =
        groupMemberRepository
            .findByGroupIdAndUserId(groupId, memberUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found in this group"));

    member.setRole(request.getRole());
    return groupMapper.toDTO(groupRepository.save(group));
  }

  private Group getGroupWithMembers(Long groupId) {
    return groupRepository
        .findById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
  }
}
