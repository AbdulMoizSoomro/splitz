import { describe, expect, it } from "vitest";
import {
  hasPendingSettlements,
  canLeaveGroup,
  selfRole,
  canManageMembers,
  canManageExpense,
  canDemoteMember,
  canPromoteMember,
  canRemoveMember,
  canChangeRole,
  useGroupGovernance,
  type PendingSettlementLike,
  type GroupLike,
} from "./membershipGating";

const pending: PendingSettlementLike = {
  payerId: 10,
  payeeId: 20,
  status: "MARKED_PAID",
  allocations: [{ groupId: 1, amount: 5 }],
};

describe("hasPendingSettlements", () => {
  it("is true for a MARKED_PAID group settlement involving the user", () => {
    expect(hasPendingSettlements([pending], 10)).toBe(true);
    expect(hasPendingSettlements([pending], 20)).toBe(true);
  });

  it("ignores global (direct) settlements", () => {
    const globalLike = { ...pending, allocations: [{ groupId: null, amount: 5 }] };
    expect(hasPendingSettlements([globalLike], 10)).toBe(false);
  });

  it("ignores settlements not involving the user, not pending, or absent", () => {
    expect(hasPendingSettlements([pending], 99)).toBe(false);
    expect(hasPendingSettlements([{ ...pending, status: "PENDING" }], 10)).toBe(false);
    expect(hasPendingSettlements([{ ...pending, status: "COMPLETED" }], 10)).toBe(false);
    expect(hasPendingSettlements(undefined, 10)).toBe(false);
  });
});

describe("canLeaveGroup — the Settled Membership Invariant", () => {
  it("allows leaving when balance is zero and there are no pending settlements", () => {
    expect(canLeaveGroup({ balance: 0, settlements: [], currentUserId: 10 })).toBe(true);
  });

  it("forbids leaving with a non-zero balance", () => {
    expect(canLeaveGroup({ balance: -12.5, settlements: [], currentUserId: 10 })).toBe(false);
  });

  it("forbids leaving with a pending settlement even at zero balance", () => {
    expect(canLeaveGroup({ balance: 0, settlements: [pending], currentUserId: 10 })).toBe(false);
  });
});

describe("role classification", () => {
  const group: GroupLike = {
    createdBy: 1,
    members: [
      { userId: 1, role: "ADMIN" },
      { userId: 2, role: "ADMIN" },
      { userId: 3, role: "MEMBER" },
    ],
  };

  it("the creator is the owner", () => {
    expect(selfRole(group, 1)).toBe("OWNER");
  });

  it("a member with the ADMIN role is an admin", () => {
    expect(selfRole(group, 2)).toBe("ADMIN");
  });

  it("anyone else is a member", () => {
    expect(selfRole(group, 3)).toBe("MEMBER");
    expect(selfRole(group, 99)).toBe("MEMBER");
    expect(selfRole(undefined, 1)).toBe("MEMBER");
  });
});

describe("canManageMembers — the Group Governance Setting exemption", () => {
  it("owner and admin manage members regardless of the setting", () => {
    expect(canManageMembers("OWNER", false)).toBe(true);
    expect(canManageMembers("ADMIN", false)).toBe(true);
  });

  it("members manage members only when the setting allows it", () => {
    expect(canManageMembers("MEMBER", true)).toBe(true);
    expect(canManageMembers("MEMBER", false)).toBe(false);
  });
});

describe("canManageExpense — Expense Management Security", () => {
  const group: GroupLike = {
    createdBy: 1,
    members: [
      { userId: 1, role: "ADMIN" },
      { userId: 2, role: "ADMIN" },
      { userId: 3, role: "MEMBER" },
    ],
    allowMembersToEditExpenses: false,
  };

  it("allows owner and admin to manage any expense", () => {
    expect(canManageExpense({ group, userId: 1, expense: { paidBy: 3 } })).toBe(true);
    expect(canManageExpense({ group, userId: 2, expense: { paidBy: 3 } })).toBe(true);
  });

  it("allows payer to manage their own expense regardless of group setting", () => {
    expect(canManageExpense({ group, userId: 3, expense: { paidBy: 3 } })).toBe(true);
  });

  it("prevents non-payer members from managing expenses when allowMembersToEditExpenses is false", () => {
    expect(canManageExpense({ group, userId: 3, expense: { paidBy: 1 } })).toBe(false);
  });

  it("allows non-payer members to manage expenses when allowMembersToEditExpenses is true", () => {
    const openGroup = { ...group, allowMembersToEditExpenses: true };
    expect(canManageExpense({ group: openGroup, userId: 3, expense: { paidBy: 1 } })).toBe(true);
  });
});

describe("Ownership Invariant & Peer Removal Authority", () => {
  describe("canDemoteMember", () => {
    it("allows owner to demote an admin", () => {
      expect(canDemoteMember({ actorRole: "OWNER", targetUserId: 2, ownerUserId: 1 })).toBe(true);
    });

    it("prevents admin from demoting another admin (only owner can demote admins)", () => {
      expect(canDemoteMember({ actorRole: "ADMIN", targetUserId: 2, ownerUserId: 1 })).toBe(false);
    });

    it("prevents demoting the owner", () => {
      expect(canDemoteMember({ actorRole: "OWNER", targetUserId: 1, ownerUserId: 1 })).toBe(false);
    });
  });

  describe("canPromoteMember", () => {
    it("allows owner and admin to promote a member to admin", () => {
      expect(canPromoteMember({ actorRole: "OWNER", targetUserId: 3, ownerUserId: 1 })).toBe(true);
      expect(canPromoteMember({ actorRole: "ADMIN", targetUserId: 3, ownerUserId: 1 })).toBe(true);
    });

    it("prevents a regular member from promoting anyone", () => {
      expect(canPromoteMember({ actorRole: "MEMBER", targetUserId: 3, ownerUserId: 1 })).toBe(false);
    });

    it("prevents promoting the owner", () => {
      expect(canPromoteMember({ actorRole: "OWNER", targetUserId: 1, ownerUserId: 1 })).toBe(false);
    });
  });

  describe("canRemoveMember", () => {
    it("prevents removing the owner under any circumstances", () => {
      const res = canRemoveMember({
        actorUserId: 2,
        actorRole: "ADMIN",
        targetUserId: 1,
        targetRole: "OWNER",
        ownerUserId: 1,
      });
      expect(res.allowed).toBe(false);
      expect(res.reason).toMatch(/owner/i);
    });

    it("enforces Peer Removal Authority: Admin CAN remove another Admin if settled", () => {
      const res = canRemoveMember({
        actorUserId: 2,
        actorRole: "ADMIN",
        targetUserId: 4,
        targetRole: "ADMIN",
        ownerUserId: 1,
        targetBalance: 0,
        targetSettlements: [],
      });
      expect(res.allowed).toBe(true);
    });

    it("blocks removal of a member with non-zero balance (Settled Membership Invariant)", () => {
      const res = canRemoveMember({
        actorUserId: 1,
        actorRole: "OWNER",
        targetUserId: 3,
        targetRole: "MEMBER",
        ownerUserId: 1,
        targetBalance: -10,
      });
      expect(res.allowed).toBe(false);
      expect(res.reason).toMatch(/balance/i);
    });

    it("blocks removal of a member with pending settlements (Settled Membership Invariant)", () => {
      const targetPending: PendingSettlementLike = {
        payerId: 3,
        payeeId: 20,
        status: "MARKED_PAID",
        allocations: [{ groupId: 1, amount: 5 }],
      };
      const res = canRemoveMember({
        actorUserId: 1,
        actorRole: "OWNER",
        targetUserId: 3,
        targetRole: "MEMBER",
        ownerUserId: 1,
        targetBalance: 0,
        targetSettlements: [targetPending],
      });
      expect(res.allowed).toBe(false);
      expect(res.reason).toMatch(/pending/i);
    });
  });

  describe("canChangeRole", () => {
    it("flags self-demotion confirmation for admin demoting self", () => {
      const res = canChangeRole({
        actorUserId: 2,
        actorRole: "ADMIN",
        targetUserId: 2,
        currentRole: "ADMIN",
        newRole: "MEMBER",
        ownerUserId: 1,
      });
      expect(res.allowed).toBe(true);
      expect(res.requiresSelfDemoteConfirm).toBe(true);
    });
  });
});

describe("useGroupGovernance Hook", () => {
  const group: GroupLike = {
    createdBy: 1,
    members: [
      { userId: 1, role: "ADMIN" },
      { userId: 2, role: "ADMIN" },
      { userId: 3, role: "MEMBER" },
    ],
    allowMembersToManageMembers: false,
    allowMembersToEditExpenses: false,
  };

  it("provides governance capabilities for owner", () => {
    const gov = useGroupGovernance({
      group,
      currentUserId: 1,
      currentUserBalance: 0,
      settlements: [],
    });

    expect(gov.currentUserRole).toBe("OWNER");
    expect(gov.canManageMembers).toBe(true);
    expect(gov.canLeave).toBe(false); // Owner in multi-member group must transfer ownership
    expect(gov.leaveReason).toMatch(/transfer ownership/i);
    expect(gov.canManageRoleFor(2)).toBe(true);
    expect(gov.canDemoteMember(2)).toBe(true);
  });

  it("provides governance capabilities for admin", () => {
    const gov = useGroupGovernance({
      group,
      currentUserId: 2,
      currentUserBalance: 0,
      settlements: [],
    });

    expect(gov.currentUserRole).toBe("ADMIN");
    expect(gov.canManageMembers).toBe(true);
    expect(gov.canLeave).toBe(true);
    expect(gov.canManageRoleFor(3)).toBe(true);
    expect(gov.canDemoteMember(2)).toBe(true); // Self-demote
    expect(gov.canDemoteMember(1)).toBe(false); // Owner cannot be demoted
  });
});

