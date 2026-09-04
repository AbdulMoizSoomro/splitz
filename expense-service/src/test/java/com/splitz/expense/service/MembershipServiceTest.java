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

    membershipService.addMember(10L, request, 2L);

    verify(groupRepository).save(any(Group.class));
  }

  @Test
  void addMember_GovernanceFails_ShouldThrowUnauthorized() {
    AddMemberRequest request = new AddMemberRequest();
    request.setUserId(4L);

    doThrow(new UnauthorizedException("You do not have permission"))
        .when(groupGovernance)
        .assertCanManageMembers(10L, 3L);

    assertThrows(UnauthorizedException.class, () -> membershipService.addMember(10L, request, 3L));
  }

  @Test
  void addMember_AlreadyInGroup_ShouldThrowIllegalArgument() {
    AddMemberRequest request = new AddMemberRequest();
    request.setUserId(3L);

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
  void removeMember_ShouldInvokeGovernanceAndRemove() {
    doNothing().when(groupGovernance).assertCanRemoveMember(10L, 1L, 2L);
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L))
        .thenReturn(Optional.of(adminMember));

    membershipService.removeMember(10L, 2L, 1L);

    verify(groupMemberRepository).delete(adminMember);
  }

  @Test
  void removeMember_GovernanceDenied_ShouldThrowException() {
    doThrow(new UnauthorizedException("The group owner cannot be removed"))
        .when(groupGovernance)
        .assertCanRemoveMember(10L, 1L, 1L);

    assertThrows(UnauthorizedException.class, () -> membershipService.removeMember(10L, 1L, 1L));
  }

  @Test
  void updateMemberRole_ShouldInvokeGovernanceAndUpdate() {
    UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
    request.setRole(GroupRole.MEMBER);

    doNothing().when(groupGovernance).assertCanChangeRole(10L, 1L, 2L, GroupRole.MEMBER);
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L))
        .thenReturn(Optional.of(adminMember));
    when(groupRepository.save(any(Group.class))).thenReturn(group);
    when(groupMapper.toDTO(any(Group.class))).thenReturn(new GroupDTO());

    membershipService.updateMemberRole(10L, 2L, request, 1L);

    verify(groupRepository).save(any(Group.class));
    assertEquals(GroupRole.MEMBER, adminMember.getRole());
  }

  // --- initializeGroupMembers Tests ---

  @Test
  void initializeGroupMembers_AddsCreatorAsAdminAndReturnsDTO() {
    Group freshGroup = Group.builder().id(20L).name("Trip").createdBy(1L).active(true).build();
    when(groupRepository.findById(20L)).thenReturn(Optional.of(freshGroup));
    when(userClient.existsById(2L)).thenReturn(true);
    when(groupRepository.save(any(Group.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(groupMapper.toDTO(any(Group.class))).thenReturn(GroupDTO.builder().id(20L).build());

    GroupDTO result = membershipService.initializeGroupMembers(20L, 1L, List.of(2L));

    org.junit.jupiter.api.Assertions.assertNotNull(result);
    assertEquals(20L, result.getId());
    assertEquals(2, freshGroup.getMembers().size());
    org.junit.jupiter.api.Assertions.assertTrue(
        freshGroup.getMembers().stream()
            .anyMatch(m -> m.getUserId().equals(1L) && m.getRole() == GroupRole.ADMIN));
    org.junit.jupiter.api.Assertions.assertTrue(
        freshGroup.getMembers().stream()
            .anyMatch(m -> m.getUserId().equals(2L) && m.getRole() == GroupRole.MEMBER));
  }

  @Test
  void initializeGroupMembers_WhenNullOrEmptyMembers_ShouldAddOnlyCreator() {
    Group freshGroup = Group.builder().id(20L).name("Trip").createdBy(1L).active(true).build();
    when(groupRepository.findById(20L)).thenReturn(Optional.of(freshGroup));
    when(groupRepository.save(any(Group.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(groupMapper.toDTO(any(Group.class))).thenReturn(GroupDTO.builder().id(20L).build());

    GroupDTO result = membershipService.initializeGroupMembers(20L, 1L, null);

    org.junit.jupiter.api.Assertions.assertNotNull(result);
    assertEquals(1, freshGroup.getMembers().size());
    GroupMember creator = freshGroup.getMembers().iterator().next();
    assertEquals(1L, creator.getUserId());
    assertEquals(GroupRole.ADMIN, creator.getRole());
  }

  @Test
  void initializeGroupMembers_WithCreatorAndDuplicates_ShouldDeduplicate() {
    Group freshGroup = Group.builder().id(20L).name("Trip").createdBy(1L).active(true).build();
    when(groupRepository.findById(20L)).thenReturn(Optional.of(freshGroup));
    when(userClient.existsById(2L)).thenReturn(true);
    when(groupRepository.save(any(Group.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(groupMapper.toDTO(any(Group.class))).thenReturn(GroupDTO.builder().id(20L).build());

    GroupDTO result = membershipService.initializeGroupMembers(20L, 1L, List.of(1L, 2L, 2L));

    org.junit.jupiter.api.Assertions.assertNotNull(result);
    assertEquals(2, freshGroup.getMembers().size());
    org.junit.jupiter.api.Assertions.assertTrue(
        freshGroup.getMembers().stream()
            .anyMatch(m -> m.getUserId().equals(1L) && m.getRole() == GroupRole.ADMIN));
    org.junit.jupiter.api.Assertions.assertTrue(
        freshGroup.getMembers().stream()
            .anyMatch(m -> m.getUserId().equals(2L) && m.getRole() == GroupRole.MEMBER));
    verify(userClient, times(1)).existsById(2L);
    verify(userClient, never()).existsById(1L);
  }

  @Test
  void initializeGroupMembers_WhenUserNotFound_ShouldThrowResourceNotFoundException() {
    Group freshGroup = Group.builder().id(20L).name("Trip").createdBy(1L).active(true).build();
    when(groupRepository.findById(20L)).thenReturn(Optional.of(freshGroup));
    when(userClient.existsById(999L)).thenReturn(false);

    assertThrows(
        com.splitz.expense.exception.ResourceNotFoundException.class,
        () -> membershipService.initializeGroupMembers(20L, 1L, List.of(999L)));
    verify(groupRepository, never()).save(any(Group.class));
  }

  @Test
  void initializeGroupMembers_WhenExceeds50Members_ShouldThrowIllegalArgumentException() {
    List<Long> memberIds = new ArrayList<>();
    for (long i = 100L; i < 155L; i++) {
      memberIds.add(i);
    }

    assertThrows(
        IllegalArgumentException.class,
        () -> membershipService.initializeGroupMembers(20L, 1L, memberIds));
    verify(groupRepository, never()).save(any(Group.class));
  }
}
