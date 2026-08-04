import { isGlobalPayment } from "../balances/settlement";

/**
 * Membership rules for a Group, owned by one module instead of being
 * re-derived inside each view.
 *
 * GroupDetails used to compute `hasPendingSettlements` and `canLeave` inline
 * (and the role flags for who can manage members) — the same "Settled
 * Membership Invariant" the backend Membership Module enforces, re-invented
 * as untested view code. This module is the small, pure interface for that
 * rule set: the invariant, plus how the creator/admin/owner roles gate the
 * Group Governance Setting.
 */

export type SelfRole = "OWNER" | "ADMIN" | "MEMBER";

/** The subset of a settlement a Membership view needs to gate on. */
export interface PendingSettlementLike {
  payerId: number;
  payeeId: number;
  status: string;
  allocations?: Array<{ groupId: number | null; amount: number }>;
}

/** The subset of a Group (and its members) a role decision needs. */
export interface GroupLike {
  createdBy: number;
  members?: Array<{ userId: number; role: "ADMIN" | "MEMBER" }>;
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
        s.status === "MARKED_PAID" &&
        !isGlobalPayment(s),
    ) ?? false
  );
}

/**
 * The Settled Membership Invariant (CONTEXT.md): a user cannot leave a group
 * while they have a non-zero balance or any pending/paid settlement.
 */
export function canLeaveGroup(params: {
  balance: number;
  settlements?: PendingSettlementLike[];
  currentUserId: number;
}): boolean {
  return (
    params.balance === 0 &&
    !hasPendingSettlements(params.settlements, params.currentUserId)
  );
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
  allowMembersToManageMembers: boolean,
): boolean {
  return role !== "MEMBER" || allowMembersToManageMembers;
}
