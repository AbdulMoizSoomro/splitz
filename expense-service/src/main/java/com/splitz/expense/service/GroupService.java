package com.splitz.expense.service;

import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.AddMemberRequest;
import com.splitz.expense.dto.CreateGroupRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.UpdateGroupRequest;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.mapper.GroupMapper;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
    requireAdmin(group, userId);

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

  public void removeMember(Long groupId, Long memberUserId, Long userId) {
    Group group = getGroupWithMembers(groupId);
    requireAdmin(group, userId);

    GroupMember member =
        groupMemberRepository
            .findByGroupIdAndUserId(groupId, memberUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found in this group"));

    group.removeMember(member);
    groupMemberRepository.delete(member);
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
      throw new AccessDeniedException("You are not a member of this group");
    }
  }

  private void requireAdmin(Group group, Long userId) {
    GroupMember membership =
        group.getMembers().stream()
            .filter(member -> member.getUserId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new AccessDeniedException("You are not a member of this group"));
    if (membership.getRole() != GroupRole.ADMIN) {
      throw new AccessDeniedException("Only admins can perform this action");
    }
  }
}
