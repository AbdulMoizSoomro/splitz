package com.splitz.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.AddMemberRequest;
import com.splitz.expense.dto.BulkAddMembersRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.UpdateMemberRoleRequest;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.governance.GroupGovernance;
import com.splitz.expense.mapper.GroupMapper;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

  @Mock private GroupRepository groupRepository;
  @Mock private GroupMemberRepository groupMemberRepository;
  @Mock private GroupMapper groupMapper;
  @Mock private UserClient userClient;
  @Mock private GroupGovernance groupGovernance;

  @InjectMocks private MembershipService membershipService;

  private Group group;
  private GroupMember ownerMember;
  private GroupMember adminMember;
  private GroupMember standardMember;

  @BeforeEach
  void setUp() {
    ownerMember = GroupMember.builder().userId(1L).role(GroupRole.ADMIN).build();
    adminMember = GroupMember.builder().userId(2L).role(GroupRole.ADMIN).build();
    standardMember = GroupMember.builder().userId(3L).role(GroupRole.MEMBER).build();

    group =
        Group.builder()
            .id(10L)
            .name("Apartment 4B")
            .createdBy(1L) // User 1 is Owner
            .members(new java.util.HashSet<>(Set.of(ownerMember, adminMember, standardMember)))
            .allowMembersToManageMembers(false)
            .active(true)
            .build();

    ownerMember.setGroup(group);
    adminMember.setGroup(group);
    standardMember.setGroup(group);
  }

  @Test
  void addMember_AsAdmin_ShouldSucceed() {
    AddMemberRequest request = new AddMemberRequest();
    request.setUserId(4L);
    request.setRole(GroupRole.MEMBER);

    doNothing().when(groupGovernance).assertCanManageMembers(10L, 2L);
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(10L, 4L)).thenReturn(false);
    when(userClient.existsById(4L)).thenReturn(true);
    when(groupRepository.save(any(Group.class))).thenReturn(group);
    when(groupMapper.toDTO(any(Group.class))).thenReturn(new GroupDTO());

    membershipService.addMember(10L, request, 2L); // User 2 is Admin

    verify(groupRepository).save(any(Group.class));
  }

  @Test
  void addMember_AsMemberSettingsDisabled_ShouldThrowUnauthorized() {
    AddMemberRequest request = new AddMemberRequest();
    request.setUserId(4L);

    doThrow(new UnauthorizedException("You do not have permission to manage members in this group"))
        .when(groupGovernance)
        .assertCanManageMembers(10L, 3L);

    assertThrows(
        UnauthorizedException.class,
        () -> membershipService.addMember(10L, request, 3L)); // User 3 is regular Member
  }

  @Test
  void addMember_AsMemberSettingsEnabled_ShouldSucceed() {
    AddMemberRequest request = new AddMemberRequest();
    request.setUserId(4L);

    doNothing().when(groupGovernance).assertCanManageMembers(10L, 3L);
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(10L, 4L)).thenReturn(false);
    when(userClient.existsById(4L)).thenReturn(true);
    when(groupRepository.save(any(Group.class))).thenReturn(group);
    when(groupMapper.toDTO(any(Group.class))).thenReturn(new GroupDTO());

    membershipService.addMember(10L, request, 3L); // User 3 is regular Member but settings enabled

    verify(groupRepository).save(any(Group.class));
  }

  @Test
  void addMember_AlreadyInGroup_ShouldThrowIllegalArgument() {
    AddMemberRequest request = new AddMemberRequest();
    request.setUserId(3L); // User 3 is already a member

    doNothing().when(groupGovernance).assertCanManageMembers(10L, 1L);
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(10L, 3L)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> membershipService.addMember(10L, request, 1L));
  }

  @Test
  void bulkAddMembers_OverLimit_ShouldThrowIllegalArgument() {
    BulkAddMembersRequest request = new BulkAddMembersRequest();
    List<Long> ids = new ArrayList<>();
    for (long i = 1; i <= 51; i++) {
      ids.add(i);
    }
    request.setUserIds(ids);

    assertThrows(
        IllegalArgumentException.class, () -> membershipService.bulkAddMembers(10L, request, 1L));
  }

  @Test
  void removeMember_OwnerSelfRemoval_ShouldThrowUnauthorized() {
    doThrow(new UnauthorizedException("The group owner cannot be removed from the group"))
        .when(groupGovernance)
        .assertCanRemoveMember(10L, 1L, 1L);

    assertThrows(UnauthorizedException.class, () -> membershipService.removeMember(10L, 1L, 1L));
  }

  @Test
  void removeMember_NonMemberActing_ShouldThrowUnauthorized() {
    doThrow(new UnauthorizedException("You are not a member of this group"))
        .when(groupGovernance)
        .assertCanRemoveMember(10L, 99L, 3L);

    assertThrows(
        UnauthorizedException.class,
        () -> membershipService.removeMember(10L, 3L, 99L)); // User 99 not in group
  }

  @Test
  void removeMember_AdminRemovingAdmin_ShouldSucceedWhenInvariantsMet() {
    doNothing().when(groupGovernance).assertCanRemoveMember(10L, 1L, 2L);
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L))
        .thenReturn(Optional.of(adminMember));

    membershipService.removeMember(10L, 2L, 1L); // Owner removes Admin

    verify(groupMemberRepository).delete(adminMember);
  }

  @Test
  void removeMember_WithNonZeroBalance_ShouldThrowIllegalState() {
    doThrow(new IllegalStateException("Cannot remove member with non-zero balance"))
        .when(groupGovernance)
        .assertCanRemoveMember(10L, 1L, 3L);

    assertThrows(
        IllegalStateException.class,
        () -> membershipService.removeMember(10L, 3L, 1L)); // Attempt removal by Owner
  }

  @Test
  void removeMember_WithActiveSettlements_ShouldThrowIllegalState() {
    doThrow(new IllegalStateException("Cannot remove member with active settlements"))
        .when(groupGovernance)
        .assertCanRemoveMember(10L, 1L, 3L);

    assertThrows(IllegalStateException.class, () -> membershipService.removeMember(10L, 3L, 1L));
  }

  @Test
  void updateMemberRole_ChangeOwnerRole_ShouldThrowUnauthorized() {
    UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
    request.setRole(GroupRole.MEMBER);

    doThrow(new UnauthorizedException("The group owner role cannot be modified"))
        .when(groupGovernance)
        .assertCanChangeRole(10L, 1L, 1L, GroupRole.MEMBER);

    assertThrows(
        UnauthorizedException.class,
        () -> membershipService.updateMemberRole(10L, 1L, request, 1L));
  }

  @Test
  void updateMemberRole_AdminDemotesAdmin_ShouldThrowUnauthorized() {
    UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
    request.setRole(GroupRole.MEMBER);

    doThrow(new UnauthorizedException("Only the group owner can demote another admin"))
        .when(groupGovernance)
        .assertCanChangeRole(10L, 2L, 2L, GroupRole.MEMBER);

    // User 2 is Admin, User 2 (acting) is not Owner (who is User 1)
    assertThrows(
        UnauthorizedException.class,
        () ->
            membershipService.updateMemberRole(
                10L, 2L, request, 2L)); // Admin tries to self-demote or demote other admin
  }

  @Test
  void updateMemberRole_OwnerDemotesAdmin_ShouldSucceed() {
    UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
    request.setRole(GroupRole.MEMBER);

    doNothing().when(groupGovernance).assertCanChangeRole(10L, 1L, 2L, GroupRole.MEMBER);
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L))
        .thenReturn(Optional.of(adminMember));
    when(groupRepository.save(any(Group.class))).thenReturn(group);
    when(groupMapper.toDTO(any(Group.class))).thenReturn(new GroupDTO());

    membershipService.updateMemberRole(10L, 2L, request, 1L); // Owner demotes Admin

    verify(groupRepository).save(any(Group.class));
    assertEquals(GroupRole.MEMBER, adminMember.getRole());
  }
}
