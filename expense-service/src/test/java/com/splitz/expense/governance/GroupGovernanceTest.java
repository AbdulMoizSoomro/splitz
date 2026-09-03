package com.splitz.expense.governance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.splitz.expense.balance.FinancialLedgerEngine;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.PaymentRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupGovernanceTest {

  @Mock private GroupRepository groupRepository;
  @Mock private GroupMemberRepository groupMemberRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private FinancialLedgerEngine financialLedgerEngine;
  @Mock private SharedSecurityAuthorizer splitzAuthorizer;

  @InjectMocks private DefaultGroupGovernanceEngine groupGovernance;

  private Group group;
  private GroupMember member;
  private GroupMember admin;

  @BeforeEach
  void setUp() {
    member = GroupMember.builder().userId(100L).role(GroupRole.MEMBER).build();
    admin = GroupMember.builder().userId(200L).role(GroupRole.ADMIN).build();

    group =
        Group.builder()
            .id(1L)
            .name("Test Group")
            .createdBy(200L)
            .members(new java.util.HashSet<>(Set.of(member, admin)))
            .allowMembersToManageMembers(true)
            .allowMembersToEditExpenses(true)
            .active(true)
            .build();

    member.setGroup(group);
    admin.setGroup(group);
  }

  // --- Slice 1: Membership Checks (assertIsMember & isMember) ---

  @Test
  void assertIsMember_WhenUserIsMember_ShouldSucceed() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);

    assertDoesNotThrow(() -> groupGovernance.assertIsMember(1L, 100L));
  }

  @Test
  void assertIsMember_WhenUserIsNotMember_ShouldThrowUnauthorized() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 999L)).thenReturn(false);

    assertThrows(UnauthorizedException.class, () -> groupGovernance.assertIsMember(1L, 999L));
  }

  @Test
  void assertIsMember_WhenUserIsNotMemberButIsGlobalAdmin_ShouldSucceed() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(true);

    assertDoesNotThrow(() -> groupGovernance.assertIsMember(1L, 999L));
  }

  @Test
  void assertIsMember_WhenGroupNotFound_ShouldThrowResourceNotFound() {
    when(groupRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> groupGovernance.assertIsMember(1L, 100L));
  }

  @Test
  void isMember_WhenUserIsMember_ShouldReturnTrue() {
    when(groupRepository.existsById(1L)).thenReturn(true);
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);

    assertTrue(groupGovernance.isMember(1L, 100L));
  }

  @Test
  void isMember_WhenUserIsNotMember_ShouldReturnFalse() {
    when(groupRepository.existsById(1L)).thenReturn(true);
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 999L)).thenReturn(false);

    assertFalse(groupGovernance.isMember(1L, 999L));
  }

  @Test
  void isMember_WhenGroupNotFound_ShouldReturnFalse() {
    when(groupRepository.existsById(1L)).thenReturn(false);

    assertFalse(groupGovernance.isMember(1L, 100L));
  }

  // --- Slice 2: Member Management (assertCanManageMembers) ---

  @Test
  void assertCanManageMembers_WhenUserIsAdmin_ShouldSucceed() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 200L)).thenReturn(Optional.of(admin));

    assertDoesNotThrow(() -> groupGovernance.assertCanManageMembers(1L, 200L));
  }

  @Test
  void assertCanManageMembers_WhenUserIsOwner_ShouldSucceed() {
    group.setCreatedBy(100L);
    group.setAllowMembersToManageMembers(false);

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));

    assertDoesNotThrow(() -> groupGovernance.assertCanManageMembers(1L, 100L));
  }

  @Test
  void assertCanManageMembers_WhenUserIsMemberAndSettingsEnabled_ShouldSucceed() {
    group.setAllowMembersToManageMembers(true);

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));

    assertDoesNotThrow(() -> groupGovernance.assertCanManageMembers(1L, 100L));
  }

  @Test
  void assertCanManageMembers_WhenUserIsMemberAndSettingsDisabled_ShouldThrowUnauthorized() {
    group.setAllowMembersToManageMembers(false);

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));

    assertThrows(
        UnauthorizedException.class, () -> groupGovernance.assertCanManageMembers(1L, 100L));
  }

  @Test
  void assertCanManageMembers_WhenUserIsGlobalAdminAndSettingsDisabled_ShouldSucceed() {
    group.setAllowMembersToManageMembers(false);

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(true);

    assertDoesNotThrow(() -> groupGovernance.assertCanManageMembers(1L, 100L));
  }

  // --- Slice 3: Expense Editing policy (assertCanEditExpense) ---

  @Test
  void assertCanEditExpense_WhenUserIsAdmin_ShouldSucceed() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 200L)).thenReturn(Optional.of(admin));

    assertDoesNotThrow(() -> groupGovernance.assertCanEditExpense(1L, 200L, 999L));
  }

  @Test
  void assertCanEditExpense_WhenUserIsPayer_ShouldSucceed() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));

    assertDoesNotThrow(() -> groupGovernance.assertCanEditExpense(1L, 100L, 100L));
  }

  @Test
  void assertCanEditExpense_WhenUserIsGroupCreator_ShouldSucceed() {
    group.setCreatedBy(100L);
    group.setAllowMembersToEditExpenses(false);

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));

    assertDoesNotThrow(() -> groupGovernance.assertCanEditExpense(1L, 100L, 999L));
  }

  @Test
  void assertCanEditExpense_WhenUserIsMemberAndSettingsEnabled_ShouldSucceed() {
    group.setAllowMembersToEditExpenses(true);

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));

    assertDoesNotThrow(() -> groupGovernance.assertCanEditExpense(1L, 100L, 999L));
  }

  @Test
  void assertCanEditExpense_WhenUserIsMemberAndSettingsDisabled_ShouldThrowUnauthorized() {
    group.setAllowMembersToEditExpenses(false);

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));

    assertThrows(
        UnauthorizedException.class, () -> groupGovernance.assertCanEditExpense(1L, 100L, 999L));
  }

  @Test
  void assertCanEditExpense_WhenUserIsGlobalAdminAndSettingsDisabled_ShouldSucceed() {
    group.setAllowMembersToEditExpenses(false);

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(true);

    assertDoesNotThrow(() -> groupGovernance.assertCanEditExpense(1L, 100L, 999L));
  }

  @Test
  void assertCanEditExpense_WhenUserIsNotMember_ShouldThrowUnauthorized() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 999L)).thenReturn(Optional.empty());

    assertThrows(
        UnauthorizedException.class, () -> groupGovernance.assertCanEditExpense(1L, 999L, 888L));
  }

  // --- Slice 4: Role Modifications (assertCanChangeRole) ---

  @Test
  void assertCanChangeRole_WhenTargetIsOwner_ShouldThrowUnauthorized() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 200L)).thenReturn(Optional.of(admin));

    assertThrows(
        UnauthorizedException.class,
        () -> groupGovernance.assertCanChangeRole(1L, 200L, 200L, GroupRole.MEMBER));
  }

  @Test
  void assertCanChangeRole_WhenActorIsNotAdmin_ShouldThrowUnauthorized() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));

    assertThrows(
        UnauthorizedException.class,
        () -> groupGovernance.assertCanChangeRole(1L, 100L, 100L, GroupRole.ADMIN));
  }

  @Test
  void assertCanChangeRole_WhenAdminDemotesAdmin_ShouldThrowUnauthorized() {
    GroupMember actorAdmin =
        GroupMember.builder().userId(300L).role(GroupRole.ADMIN).group(group).build();
    GroupMember targetAdmin =
        GroupMember.builder().userId(400L).role(GroupRole.ADMIN).group(group).build();
    group.getMembers().add(actorAdmin);
    group.getMembers().add(targetAdmin);

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 400L))
        .thenReturn(Optional.of(targetAdmin));
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 300L))
        .thenReturn(Optional.of(actorAdmin));

    assertThrows(
        UnauthorizedException.class,
        () -> groupGovernance.assertCanChangeRole(1L, 300L, 400L, GroupRole.MEMBER));
  }

  @Test
  void assertCanChangeRole_WhenOwnerDemotesAdmin_ShouldSucceed() {
    GroupMember targetAdmin =
        GroupMember.builder().userId(300L).role(GroupRole.ADMIN).group(group).build();
    group.getMembers().add(targetAdmin);

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 300L))
        .thenReturn(Optional.of(targetAdmin));
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 200L)).thenReturn(Optional.of(admin));

    assertDoesNotThrow(() -> groupGovernance.assertCanChangeRole(1L, 200L, 300L, GroupRole.MEMBER));
  }

  @Test
  void assertCanChangeRole_WhenGlobalAdminModifiesRole_ShouldSucceed() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(true);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));

    assertDoesNotThrow(() -> groupGovernance.assertCanChangeRole(1L, 999L, 100L, GroupRole.ADMIN));
  }

  @Test
  void assertCanChangeRole_WhenTargetNotFound_ShouldThrowResourceNotFound() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 999L)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> groupGovernance.assertCanChangeRole(1L, 200L, 999L, GroupRole.ADMIN));
  }

  @Test
  void assertCanChangeRole_WhenActorNotFound_ShouldThrowUnauthorized() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 999L)).thenReturn(Optional.empty());

    assertThrows(
        UnauthorizedException.class,
        () -> groupGovernance.assertCanChangeRole(1L, 999L, 100L, GroupRole.ADMIN));
  }

  // --- Slice 5: Member Removal & Leaving (assertCanRemoveMember) ---

  @Test
  void assertCanRemoveMember_WhenTargetIsOwner_ShouldThrowUnauthorized() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 200L)).thenReturn(Optional.of(admin));

    UnauthorizedException exception =
        assertThrows(
            UnauthorizedException.class,
            () -> groupGovernance.assertCanRemoveMember(1L, 200L, 200L));
    assertEquals("The group owner cannot be removed from the group", exception.getMessage());
  }

  @Test
  void assertCanRemoveMember_WhenTargetNotFound_ShouldThrowResourceNotFound() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 999L)).thenReturn(Optional.empty());

    ResourceNotFoundException exception =
        assertThrows(
            ResourceNotFoundException.class,
            () -> groupGovernance.assertCanRemoveMember(1L, 200L, 999L));
    assertEquals("Member not found in this group", exception.getMessage());
  }

  @Test
  void assertCanRemoveMember_WhenActorNotFound_ShouldThrowUnauthorized() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 999L)).thenReturn(Optional.empty());

    UnauthorizedException exception =
        assertThrows(
            UnauthorizedException.class,
            () -> groupGovernance.assertCanRemoveMember(1L, 999L, 100L));
    assertEquals("You are not a member of this group", exception.getMessage());
  }

  @Test
  void assertCanRemoveMember_WhenActorNotAdminRemovingOther_ShouldThrowUnauthorized() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);

    GroupMember targetMember =
        GroupMember.builder().userId(300L).role(GroupRole.MEMBER).group(group).build();
    group.getMembers().add(targetMember);

    when(groupMemberRepository.findByGroupIdAndUserId(1L, 300L))
        .thenReturn(Optional.of(targetMember));
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));

    UnauthorizedException exception =
        assertThrows(
            UnauthorizedException.class,
            () -> groupGovernance.assertCanRemoveMember(1L, 100L, 300L));
    assertEquals("Only admins can perform this action", exception.getMessage());
  }

  @Test
  void assertCanRemoveMember_WhenSelfLeaving_ShouldSucceed() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));
    when(financialLedgerEngine.calculateUserBalanceInGroup(100L, 1L))
        .thenReturn(java.math.BigDecimal.ZERO);
    when(paymentRepository.hasActivePaymentsInGroup(eq(100L), eq(1L), any())).thenReturn(false);

    assertDoesNotThrow(() -> groupGovernance.assertCanRemoveMember(1L, 100L, 100L));
  }

  @Test
  void assertCanRemoveMember_WhenAdminRemovesOtherMember_ShouldSucceed() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 200L)).thenReturn(Optional.of(admin));
    when(financialLedgerEngine.calculateUserBalanceInGroup(100L, 1L))
        .thenReturn(java.math.BigDecimal.ZERO);
    when(paymentRepository.hasActivePaymentsInGroup(eq(100L), eq(1L), any())).thenReturn(false);

    assertDoesNotThrow(() -> groupGovernance.assertCanRemoveMember(1L, 200L, 100L));
  }

  @Test
  void assertCanRemoveMember_WhenGlobalAdminRemovesOtherMember_ShouldSucceed() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(true);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));
    when(financialLedgerEngine.calculateUserBalanceInGroup(100L, 1L))
        .thenReturn(java.math.BigDecimal.ZERO);
    when(paymentRepository.hasActivePaymentsInGroup(eq(100L), eq(1L), any())).thenReturn(false);

    assertDoesNotThrow(() -> groupGovernance.assertCanRemoveMember(1L, 999L, 100L));
  }

  @Test
  void assertCanRemoveMember_WhenNonZeroBalance_ShouldThrowIllegalState() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));
    when(financialLedgerEngine.calculateUserBalanceInGroup(100L, 1L))
        .thenReturn(new java.math.BigDecimal("10.00"));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> groupGovernance.assertCanRemoveMember(1L, 100L, 100L));
    assertEquals("Cannot remove member with non-zero balance", exception.getMessage());
  }

  @Test
  void assertCanRemoveMember_WhenActiveSettlements_ShouldThrowIllegalState() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));
    when(financialLedgerEngine.calculateUserBalanceInGroup(100L, 1L))
        .thenReturn(java.math.BigDecimal.ZERO);
    when(paymentRepository.hasActivePaymentsInGroup(eq(100L), eq(1L), any())).thenReturn(true);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> groupGovernance.assertCanRemoveMember(1L, 100L, 100L));
    assertEquals("Cannot remove member with active settlements", exception.getMessage());
  }

  // --- Slice 6: Group Management Checks (assertCanManageGroup) ---

  @Test
  void assertCanManageGroup_WhenUserIsAdmin_ShouldSucceed() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 200L)).thenReturn(Optional.of(admin));

    assertDoesNotThrow(() -> groupGovernance.assertCanManageGroup(1L, 200L));
  }

  @Test
  void assertCanManageGroup_WhenUserIsMember_ShouldThrowUnauthorized() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));

    UnauthorizedException exception =
        assertThrows(
            UnauthorizedException.class, () -> groupGovernance.assertCanManageGroup(1L, 100L));
    assertEquals("Only admins can perform this action", exception.getMessage());
  }

  @Test
  void assertCanManageGroup_WhenUserIsNotInGroup_ShouldThrowUnauthorized() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 999L)).thenReturn(Optional.empty());

    UnauthorizedException exception =
        assertThrows(
            UnauthorizedException.class, () -> groupGovernance.assertCanManageGroup(1L, 999L));
    assertEquals("You are not a member of this group", exception.getMessage());
  }

  @Test
  void assertCanManageGroup_WhenUserIsGlobalAdmin_ShouldSucceed() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(splitzAuthorizer.isAdmin()).thenReturn(true);

    assertDoesNotThrow(() -> groupGovernance.assertCanManageGroup(1L, 999L));
  }
}
