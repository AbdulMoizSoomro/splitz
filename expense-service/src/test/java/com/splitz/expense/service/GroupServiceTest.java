package com.splitz.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.AddMemberRequest;
import com.splitz.expense.dto.CreateGroupRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.UpdateGroupRequest;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.mapper.GroupMapper;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.GroupMemberRepository;
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

  @Mock private GroupMemberRepository groupMemberRepository;

  @Mock private GroupMapper groupMapper;

  @Mock private UserClient userClient;

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
  void createGroup_ShouldAddCreatorAsAdmin() {
    CreateGroupRequest request = new CreateGroupRequest();
    request.setName("Roommates");
    request.setDescription("desc");

    Group saved =
        Group.builder()
            .id(1L)
            .name("Roommates")
            .active(true)
            .members(Set.of(GroupMember.builder().userId(99L).role(GroupRole.ADMIN).build()))
            .build();

    GroupDTO dto = GroupDTO.builder().id(1L).name("Roommates").build();

    when(groupRepository.save(any(Group.class))).thenReturn(saved);
    when(groupMapper.toDTO(saved)).thenReturn(dto);

    GroupDTO result = groupService.createGroup(request, 99L);

    assertEquals(1L, result.getId());
    verify(groupRepository).save(any(Group.class));
  }

  @Test
  void updateGroup_NonMember_ShouldThrowException() {
    UpdateGroupRequest updateRequest = new UpdateGroupRequest();
    updateRequest.setName("New Name");
    when(groupRepository.findById(2L)).thenReturn(Optional.of(group));

    assertThrows(
        UnauthorizedException.class, () -> groupService.updateGroup(2L, updateRequest, 5L));
  }

  @Test
  void addMember_ShouldAddSuccessfully() {
    AddMemberRequest request = new AddMemberRequest();
    request.setUserId(100L);

    when(groupRepository.findById(2L)).thenReturn(Optional.of(group));
    when(userClient.existsById(100L)).thenReturn(true);
    when(groupMemberRepository.existsByGroupIdAndUserId(2L, 100L)).thenReturn(false);

    groupService.addMember(2L, request, 1L);

    verify(groupMemberRepository).existsByGroupIdAndUserId(2L, 100L);
  }

  @Test
  void removeMember_ShouldRemoveSuccessfully() {
    GroupMember member = GroupMember.builder().userId(2L).role(GroupRole.MEMBER).build();
    group.getMembers().add(member);
    when(groupRepository.findById(2L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.findByGroupIdAndUserId(2L, 2L)).thenReturn(Optional.of(member));

    groupService.removeMember(2L, 2L, 1L);

    verify(groupMemberRepository).delete(member);
  }

  @Test
  void canManageExpenses_Admin_ShouldReturnTrue() {
    group.setAllowMembersToEditExpenses(false);
    GroupMember adminMember = GroupMember.builder().userId(1L).role(GroupRole.ADMIN).build();
    when(groupMemberRepository.findByGroupIdAndUserId(2L, 1L)).thenReturn(Optional.of(adminMember));
    // User 1 is ADMIN
    assertEquals(true, groupService.canManageExpenses(group, 1L, 99L));
  }

  @Test
  void canManageExpenses_Payer_ShouldReturnTrue() {
    group.setAllowMembersToEditExpenses(false);
    GroupMember member = GroupMember.builder().userId(2L).role(GroupRole.MEMBER).build();
    group.addMember(member);
    when(groupMemberRepository.findByGroupIdAndUserId(2L, 2L)).thenReturn(Optional.of(member));
    // User 2 is PAYER
    assertEquals(true, groupService.canManageExpenses(group, 2L, 2L));
  }

  @Test
  void canManageExpenses_Member_FlagTrue_ShouldReturnTrue() {
    group.setAllowMembersToEditExpenses(true);
    GroupMember member = GroupMember.builder().userId(2L).role(GroupRole.MEMBER).build();
    group.addMember(member);
    when(groupMemberRepository.findByGroupIdAndUserId(2L, 2L)).thenReturn(Optional.of(member));
    // User 2 is MEMBER, flag is TRUE
    assertEquals(true, groupService.canManageExpenses(group, 2L, 99L));
  }

  @Test
  void canManageExpenses_Member_FlagFalse_ShouldReturnFalse() {
    group.setAllowMembersToEditExpenses(false);
    GroupMember member = GroupMember.builder().userId(2L).role(GroupRole.MEMBER).build();
    group.addMember(member);
    when(groupMemberRepository.findByGroupIdAndUserId(2L, 2L)).thenReturn(Optional.of(member));
    // User 2 is MEMBER, flag is FALSE, not Payer
    assertEquals(false, groupService.canManageExpenses(group, 2L, 99L));
  }
}
