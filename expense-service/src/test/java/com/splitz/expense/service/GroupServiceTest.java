package com.splitz.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.splitz.expense.dto.CreateGroupRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.UpdateGroupRequest;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.governance.GroupGovernance;
import com.splitz.expense.mapper.GroupMapper;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.GroupRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

  @Mock private GroupRepository groupRepository;
  @Mock private GroupMapper groupMapper;
  @Mock private MembershipService membershipService;
  @Mock private GroupGovernance groupGovernance;

  @InjectMocks private GroupService groupService;

  private Group group;

  @BeforeEach
  void setUp() {
    group =
        Group.builder()
            .id(2L)
            .name("Test Group")
            .createdBy(1L)
            .members(
                new java.util.HashSet<>(
                    Set.of(GroupMember.builder().userId(1L).role(GroupRole.ADMIN).build())))
            .build();
  }

  @Test
  void createGroup_ShouldSaveGroupAndDelegateToMembershipService() {
    CreateGroupRequest request = new CreateGroupRequest();
    request.setName("Roommates");
    request.setDescription("desc");
    request.setMemberUserIds(java.util.List.of(2L));

    Group saved = Group.builder().id(1L).name("Roommates").active(true).build();

    GroupDTO dto = GroupDTO.builder().id(1L).name("Roommates").build();

    when(groupRepository.save(any(Group.class))).thenReturn(saved);
    when(membershipService.initializeGroupMembers(1L, 99L, java.util.List.of(2L))).thenReturn(dto);

    GroupDTO result = groupService.createGroup(request, 99L);

    assertEquals(1L, result.getId());
    verify(groupRepository).save(any(Group.class));
    verify(membershipService).initializeGroupMembers(1L, 99L, java.util.List.of(2L));
  }

  @Test
  void getGroup_WhenMember_ShouldSucceed() {
    doNothing().when(groupGovernance).assertIsMember(2L, 1L);
    when(groupRepository.findById(2L)).thenReturn(Optional.of(group));
    when(groupMapper.toDTO(group)).thenReturn(new GroupDTO());

    groupService.getGroup(2L, 1L);

    verify(groupRepository).findById(2L);
  }

  @Test
  void getGroup_WhenNonMember_ShouldThrowUnauthorized() {
    doThrow(new UnauthorizedException("You are not a member of this group"))
        .when(groupGovernance)
        .assertIsMember(2L, 5L);
    when(groupRepository.findById(2L)).thenReturn(Optional.of(group));

    assertThrows(UnauthorizedException.class, () -> groupService.getGroup(2L, 5L));
  }

  @Test
  void updateGroup_WhenAdmin_ShouldSucceed() {
    UpdateGroupRequest updateRequest = new UpdateGroupRequest();
    updateRequest.setName("New Name");

    doNothing().when(groupGovernance).assertCanManageGroup(2L, 1L);
    when(groupRepository.findById(2L)).thenReturn(Optional.of(group));
    when(groupRepository.save(any(Group.class))).thenReturn(group);
    when(groupMapper.toDTO(any(Group.class))).thenReturn(new GroupDTO());

    groupService.updateGroup(2L, updateRequest, 1L);

    verify(groupRepository).save(any(Group.class));
  }

  @Test
  void updateGroup_Unauthorized_ShouldThrowException() {
    UpdateGroupRequest updateRequest = new UpdateGroupRequest();
    updateRequest.setName("New Name");

    doThrow(new UnauthorizedException("Only admins can perform this action"))
        .when(groupGovernance)
        .assertCanManageGroup(2L, 5L);
    when(groupRepository.findById(2L)).thenReturn(Optional.of(group));

    assertThrows(
        UnauthorizedException.class, () -> groupService.updateGroup(2L, updateRequest, 5L));
  }

  @Test
  void deleteGroup_WhenAdmin_ShouldSucceed() {
    doNothing().when(groupGovernance).assertCanManageGroup(2L, 1L);
    when(groupRepository.findById(2L)).thenReturn(Optional.of(group));
    when(groupRepository.save(any(Group.class))).thenReturn(group);

    groupService.deleteGroup(2L, 1L);

    verify(groupRepository).save(any(Group.class));
  }

  @Test
  void deleteGroup_Unauthorized_ShouldThrowException() {
    doThrow(new UnauthorizedException("Only admins can perform this action"))
        .when(groupGovernance)
        .assertCanManageGroup(2L, 5L);
    when(groupRepository.findById(2L)).thenReturn(Optional.of(group));

    assertThrows(UnauthorizedException.class, () -> groupService.deleteGroup(2L, 5L));
  }
}
