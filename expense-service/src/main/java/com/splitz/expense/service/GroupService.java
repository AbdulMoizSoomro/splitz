package com.splitz.expense.service;

import com.splitz.expense.dto.CreateGroupRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.UpdateGroupRequest;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.governance.GroupGovernance;
import com.splitz.expense.mapper.GroupMapper;
import com.splitz.expense.model.Group;
import com.splitz.expense.repository.GroupRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupService {

  private final GroupRepository groupRepository;
  private final GroupMapper groupMapper;
  private final MembershipService membershipService;
  private final GroupGovernance groupGovernance;

  public GroupDTO createGroup(CreateGroupRequest request, Long currentUserId) {
    Group group =
        Group.builder()
            .name(request.getName())
            .description(request.getDescription())
            .imageUrl(request.getImageUrl())
            .createdBy(currentUserId)
            .active(true)
            .build();

    Group saved = groupRepository.save(group);
    return membershipService.initializeGroupMembers(
        saved.getId(), currentUserId, request.getMemberUserIds());
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
    groupGovernance.assertIsMember(groupId, userId);
    return groupMapper.toDTO(group);
  }

  public GroupDTO updateGroup(Long groupId, UpdateGroupRequest request, Long userId) {
    Group group = getGroupWithMembers(groupId);
    groupGovernance.assertCanManageGroup(groupId, userId);

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
    groupGovernance.assertCanManageGroup(groupId, userId);
    group.setActive(false);
    groupRepository.save(group);
  }

  private Group getGroupWithMembers(Long groupId) {
    return groupRepository
        .findById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
  }
}
