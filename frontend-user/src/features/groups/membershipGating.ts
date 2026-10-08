import { isGlobalPayment } from "../balances/settlement";

/**
 * Membership rules & Governance Security Module for Group.
 *
 * Encapsulates the core security invariants:
 * 1. Settled Membership Invariant: non-zero balance or pending settlements block leaving/removal.
 * 2. Ownership Invariant: group has exactly 1 owner; owner cannot be demoted or removed.
 * 3. Peer Removal Authority: admins can remove admins; only owner can demote admins.
 * 4. Capability Gating: member addition, expense management, and role management permissions.
 */

export type SelfRole = "OWNER" | "ADMIN" | "MEMBER";

/** The subset of a settlement a Membership view needs to gate on. */
export interface PendingSettlementLike {
  payerId: number;
  payeeId: number;
  status: string;
  allocations?: Array<{ groupId: number | null; amount: number }>;
}

/** The subset of a Group (and its members) a governance decision needs. */
export interface GroupLike {
  createdBy: number;
  members?: Array<{ userId: number; role: "ADMIN" | "MEMBER" }>;
  allowMembersToManageMembers?: boolean;
  allowMembersToEditExpenses?: boolean;
}

export interface InvariantResult {
  allowed: boolean;
  reason?: string;
}

/**
 * True when the user has a marked-paid group settlement that has not been
 * confirmed — i.e. money is still moving. Direct/global settlements are
 * excluded because they follow the personal (friend) ledger, not the group.
 */
export function hasPendingSettlements(
  settlements: PendingSettlementLike[] | undefined,
  currentUserId: number,
): boolean {
  return (
    settlements?.some(
      (s) =>
        (s.payerId === currentUserId || s.payeeId === currentUserId) &&
        (s.status === "MARKED_PAID" || s.status === "PENDING") &&
        !isGlobalPayment(s),
    ) ?? false
  );
}

/**
 * The Settled Membership Invariant (CONTEXT.md): a user cannot leave a group
 * while they have a non-zero balance or any pending/paid settlement.
 */
export function canLeaveGroup(params: {
  group?: GroupLike;
  balance: number;
  settlements?: PendingSettlementLike[];
  currentUserId: number;
}): boolean {
  return canLeaveGroupInfo(params).allowed;
}

/**
 * Detailed assessment of whether a user can leave the group, including error reasons.
 */
export function canLeaveGroupInfo(params: {
  group?: GroupLike;
  balance: number;
  settlements?: PendingSettlementLike[];
  currentUserId: number;
}): InvariantResult {
  if (hasPendingSettlements(params.settlements, params.currentUserId)) {
    return {
      allowed: false,
      reason: "You cannot leave this group while you have pending unconfirmed payments.",
    };
  }
  if (params.balance !== 0) {
    return {
      allowed: false,
      reason: `You cannot leave this group while you have an outstanding balance (${params.balance}).`,
    };
  }
  if (params.group && params.group.createdBy === params.currentUserId) {
    const memberCount = params.group.members?.length ?? 0;
    if (memberCount > 1) {
      return {
        allowed: false,
        reason: "As the group owner, you must transfer ownership to another member before leaving.",
      };
    }
  }
  return { allowed: true };
}

/** The current user's role within a group: OWNER (creator) / ADMIN / MEMBER. */
export function selfRole(
  group: GroupLike | undefined,
  currentUserId: number,
): SelfRole {
  if (!group) return "MEMBER";
  if (group.createdBy === currentUserId) return "OWNER";
  const member = group.members?.find((m) => m.userId === currentUserId);
  return member?.role === "ADMIN" ? "ADMIN" : "MEMBER";
}

/**
 * Whether a user may add/remove group members, per the Group Governance
 * Setting: OWNER and ADMIN are always exempt; a MEMBER only when the group
 * allows members to manage members.
 */
export function canManageMembers(
  role: SelfRole,
  allowMembersToManageMembers?: boolean,
): boolean {
  return role !== "MEMBER" || Boolean(allowMembersToManageMembers);
}

/**
 * Evaluates expense edit/delete authorization per group governance settings.
 */
export function canManageExpense(params: {
  group?: GroupLike;
  userId: number;
  expense: { paidBy: number };
}): boolean {
  if (!params.group) return false;
  const role = selfRole(params.group, params.userId);
  if (role === "OWNER" || role === "ADMIN") return true;
  if (params.expense.paidBy === params.userId) return true;
  return Boolean(params.group.allowMembersToEditExpenses);
}

/**
 * Checks if actor can demote target. Only OWNER can demote an ADMIN (unless self-demotion). Owner cannot be demoted.
 */
export function canDemoteMember(params: {
  actorUserId?: number;
  actorRole: SelfRole;
  targetUserId: number;
  ownerUserId: number;
}): boolean {
  if (params.targetUserId === params.ownerUserId) return false;
  if (params.actorRole === "OWNER") return true;
  if (params.actorRole === "ADMIN" && params.actorUserId === params.targetUserId) return true;
  return false;
}

/**
 * Checks if actor can promote target. OWNER and ADMIN can promote MEMBER to ADMIN.
 */
export function canPromoteMember(params: {
  actorRole: SelfRole;
  targetUserId: number;
  ownerUserId: number;
}): boolean {
  if (params.targetUserId === params.ownerUserId) return false;
  return params.actorRole === "OWNER" || params.actorRole === "ADMIN";
}

/**
 * Evaluates member removal authority & invariants (Settled Membership, Peer Removal Authority, Ownership Invariant).
 */
export function canRemoveMember(params: {
  actorUserId: number;
  actorRole: SelfRole;
  targetUserId: number;
  targetRole: SelfRole;
  ownerUserId: number;
  targetBalance?: number;
  targetSettlements?: PendingSettlementLike[];
  allowMembersToManageMembers?: boolean;
}): InvariantResult {
  if (params.targetUserId === params.ownerUserId || params.targetRole === "OWNER") {
    return { allowed: false, reason: "The group owner cannot be removed." };
  }

  // Settled Membership Invariant
  if (params.targetBalance !== undefined && params.targetBalance !== 0) {
    return { allowed: false, reason: "Member cannot be removed while having an outstanding balance." };
  }
  if (params.targetSettlements && hasPendingSettlements(params.targetSettlements, params.targetUserId)) {
    return { allowed: false, reason: "Member cannot be removed while having pending settlements." };
  }

  // Role / Peer Removal Authority checks
  if (params.actorRole === "OWNER") return { allowed: true };
  if (params.actorRole === "ADMIN") {
    // Peer Removal Authority: ADMIN can remove another ADMIN or MEMBER
    return { allowed: true };
  }
  if (params.actorRole === "MEMBER") {
    if (params.allowMembersToManageMembers && params.targetRole === "MEMBER") {
      return { allowed: true };
    }
    return { allowed: false, reason: "Regular members do not have authority to remove this member." };
  }

  return { allowed: false, reason: "Unauthorized to remove member." };
}

/**
 * Asserts and evaluates role change transitions.
 */
export function canChangeRole(params: {
  actorUserId: number;
  actorRole: SelfRole;
  targetUserId: number;
  currentRole: SelfRole;
  newRole: SelfRole;
  ownerUserId: number;
}): { allowed: boolean; reason?: string; requiresSelfDemoteConfirm?: boolean } {
  if (params.targetUserId === params.ownerUserId) {
    return { allowed: false, reason: "Owner role cannot be modified." };
  }

  if (params.newRole === "MEMBER") {
    const demoteAllowed = canDemoteMember({
      actorUserId: params.actorUserId,
      actorRole: params.actorRole,
      targetUserId: params.targetUserId,
      ownerUserId: params.ownerUserId,
    });
    if (!demoteAllowed) {
      return { allowed: false, reason: "Only the group owner can demote an admin." };
    }
    const isSelf = params.actorUserId === params.targetUserId;
    return { allowed: true, requiresSelfDemoteConfirm: isSelf };
  }

  if (params.newRole === "ADMIN") {
    const promoteAllowed = canPromoteMember({
      actorRole: params.actorRole,
      targetUserId: params.targetUserId,
      ownerUserId: params.ownerUserId,
    });
    if (!promoteAllowed) {
      return { allowed: false, reason: "You do not have authority to promote members." };
    }
    return { allowed: true };
  }

  return { allowed: false, reason: "Invalid role transition." };
}

/**
 * High-leverage React hook encapsulating Group Governance & Membership Security logic.
 */
export function useGroupGovernance(params: {
  group: GroupLike | undefined;
  currentUserId: number;
  currentUserBalance?: number;
  settlements?: PendingSettlementLike[];
}) {
  const { group, currentUserId, currentUserBalance = 0, settlements = [] } = params;
  const currentUserRole = selfRole(group, currentUserId);
  const ownerUserId = group?.createdBy ?? 0;

  const hasPending = hasPendingSettlements(settlements, currentUserId);
  const leaveInfo = canLeaveGroupInfo({
    group,
    balance: currentUserBalance,
    settlements,
    currentUserId,
  });

  const canManage = canManageMembers(currentUserRole, group?.allowMembersToManageMembers);

  return {
    currentUserRole,
    ownerUserId,
    canManageMembers: canManage,
    canLeave: leaveInfo.allowed,
    leaveReason: leaveInfo.reason,
    hasPendingSettlements: hasPending,

    canManageExpense: (expense: { paidBy: number }) =>
      canManageExpense({ group, userId: currentUserId, expense }),

    canDemoteMember: (targetUserId: number) =>
      canDemoteMember({
        actorUserId: currentUserId,
        actorRole: currentUserRole,
        targetUserId,
        ownerUserId,
      }),

    canPromoteMember: (targetUserId: number) =>
      canPromoteMember({ actorRole: currentUserRole, targetUserId, ownerUserId }),

    canRemoveMember: (
      targetUserId: number,
      targetRole: SelfRole,
      targetBalance?: number,
      targetSettlements?: PendingSettlementLike[],
    ) =>
      canRemoveMember({
        actorUserId: currentUserId,
        actorRole: currentUserRole,
        targetUserId,
        targetRole,
        ownerUserId,
        targetBalance,
        targetSettlements,
        allowMembersToManageMembers: group?.allowMembersToManageMembers,
      }),

    canChangeRole: (targetUserId: number, currentRole: SelfRole, newRole: SelfRole) =>
      canChangeRole({
        actorUserId: currentUserId,
        actorRole: currentUserRole,
        targetUserId,
        currentRole,
        newRole,
        ownerUserId,
      }),

    canManageRoleFor: (targetUserId: number) => {
      if (!group || targetUserId === ownerUserId) return false;
      return currentUserRole === "OWNER" || currentUserRole === "ADMIN";
    },
  };
}

/**
 * The governance capabilities derived for one group and one acting user.
 *
 * Derived from the hook's return type rather than hand-written, so it cannot drift from the rules the
 * hook actually enforces.
 */
export type GroupGovernance = ReturnType<typeof useGroupGovernance>;
