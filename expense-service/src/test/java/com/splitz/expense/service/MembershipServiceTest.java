package com.splitz.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.AddMemberRequest;
import com.splitz.expense.dto.BulkAddMembersRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.UpdateMemberRoleRequest;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.mapper.GroupMapper;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.math.BigDecimal;
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
  @Mock private SettlementAllocationRepository settlementAllocationRepository;
  @Mock private GroupMapper groupMapper;
  @Mock private UserClient userClient;
  @Mock private BalanceService balanceService;
  @Mock private SharedSecurityAuthorizer splitzAuthorizer;

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

    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L))
        .thenReturn(Optional.of(adminMember));
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

    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 3L))
        .thenReturn(Optional.of(standardMember));

    assertThrows(
        UnauthorizedException.class,
        () -> membershipService.addMember(10L, request, 3L)); // User 3 is regular Member
  }

  @Test
  void addMember_AsMemberSettingsEnabled_ShouldSucceed() {
    AddMemberRequest request = new AddMemberRequest();
    request.setUserId(4L);
    group.setAllowMembersToManageMembers(true);

    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 3L))
        .thenReturn(Optional.of(standardMember));
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

    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
        .thenReturn(Optional.of(ownerMember));
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
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
        .thenReturn(Optional.of(ownerMember));

    assertThrows(UnauthorizedException.class, () -> membershipService.removeMember(10L, 1L, 1L));
  }

  @Test
  void removeMember_NonMemberActing_ShouldThrowUnauthorized() {
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 3L))
        .thenReturn(Optional.of(standardMember));
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 99L)).thenReturn(Optional.empty());

    assertThrows(
        UnauthorizedException.class,
        () -> membershipService.removeMember(10L, 3L, 99L)); // User 99 not in group
  }

  @Test
  void removeMember_AdminRemovingAdmin_ShouldSucceedWhenInvariantsMet() {
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L))
        .thenReturn(Optional.of(adminMember));
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
        .thenReturn(Optional.of(ownerMember));
    when(balanceService.calculateUserBalanceInGroup(2L, 10L)).thenReturn(BigDecimal.ZERO);
    when(settlementAllocationRepository.hasActiveSettlementsForUserInGroup(eq(2L), eq(10L), any()))
        .thenReturn(false);

    membershipService.removeMember(10L, 2L, 1L); // Owner removes Admin

    verify(groupMemberRepository).delete(adminMember);
  }

  @Test
  void removeMember_WithNonZeroBalance_ShouldThrowIllegalState() {
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 3L))
        .thenReturn(Optional.of(standardMember));
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
        .thenReturn(Optional.of(ownerMember));
    when(balanceService.calculateUserBalanceInGroup(3L, 10L)).thenReturn(BigDecimal.TEN);

    assertThrows(
        IllegalStateException.class,
        () -> membershipService.removeMember(10L, 3L, 1L)); // Attempt removal by Owner
  }

  @Test
  void removeMember_WithActiveSettlements_ShouldThrowIllegalState() {
    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 3L))
        .thenReturn(Optional.of(standardMember));
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
        .thenReturn(Optional.of(ownerMember));
    when(balanceService.calculateUserBalanceInGroup(3L, 10L)).thenReturn(BigDecimal.ZERO);
    when(settlementAllocationRepository.hasActiveSettlementsForUserInGroup(eq(3L), eq(10L), any()))
        .thenReturn(true);

    assertThrows(IllegalStateException.class, () -> membershipService.removeMember(10L, 3L, 1L));
  }

  @Test
  void updateMemberRole_ChangeOwnerRole_ShouldThrowUnauthorized() {
    UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
    request.setRole(GroupRole.MEMBER);

    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
        .thenReturn(Optional.of(ownerMember));

    assertThrows(
        UnauthorizedException.class,
        () -> membershipService.updateMemberRole(10L, 1L, request, 1L));
  }

  @Test
  void updateMemberRole_AdminDemotesAdmin_ShouldThrowUnauthorized() {
    UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
    request.setRole(GroupRole.MEMBER);

    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L))
        .thenReturn(Optional.of(adminMember));

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

    when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L))
        .thenReturn(Optional.of(adminMember));
    when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
        .thenReturn(Optional.of(ownerMember));
    when(groupRepository.save(any(Group.class))).thenReturn(group);
    when(groupMapper.toDTO(any(Group.class))).thenReturn(new GroupDTO());

    membershipService.updateMemberRole(10L, 2L, request, 1L); // Owner demotes Admin

    verify(groupRepository).save(any(Group.class));
    assertEquals(GroupRole.MEMBER, adminMember.getRole());
  }
}
