package com.splitz.expense.governance;

import com.splitz.expense.model.GroupRole;

public interface GroupGovernance {
  /**
   * Asserts that a user is a member of the group, or is a global admin. Throws
   * UnauthorizedException if not authorized.
   */
  void assertIsMember(Long groupId, Long userId);

  /**
   * Asserts that an acting user has authority to manage members in the group. Respects
   * allowMembersToManageMembers setting; owners and admins are exempt. Throws UnauthorizedException
   * if not authorized.
   */
  void assertCanManageMembers(Long groupId, Long actorUserId);

  /**
   * Asserts that an acting user has authority to modify or delete an expense in the group. Respects
   * allowMembersToEditExpenses setting; admins and the payer/creator are exempt. Throws
   * UnauthorizedException if not authorized.
   */
  void assertCanEditExpense(Long groupId, Long actorUserId, Long expenseCreatorUserId);

  /**
   * Asserts that an acting user has authority to change a member's role in the group. Enforces that
   * only admins can change roles, only the owner can demote admins, and the owner's role cannot be
   * modified. Throws UnauthorizedException/IllegalArgumentException if not authorized.
   */
  void assertCanChangeRole(Long groupId, Long actorUserId, Long targetUserId, GroupRole newRole);

  /**
   * Asserts that an acting user can remove a member or self-leave the group. Verifies that the
   * actor has admin authority to remove others, the owner is untouchable, and enforces the Settled
   * Membership Invariant (zero-balance, no active settlements). Throws
   * UnauthorizedException/IllegalStateException if invariants fail.
   */
  void assertCanRemoveMember(Long groupId, Long actorUserId, Long targetUserId);

  /**
   * Asserts that an acting user has authority to manage the group itself (e.g. update, delete).
   * Enforces that only group admins (or global admins) can perform these actions. Throws
   * UnauthorizedException if not authorized.
   */
  void assertCanManageGroup(Long groupId, Long actorUserId);

  /**
   * Query checks if a user is a member of the group. Returns false when the group does not exist;
   * repository failures are propagated rather than treated as an authorization result.
   */
  boolean isMember(Long groupId, Long userId);
}
