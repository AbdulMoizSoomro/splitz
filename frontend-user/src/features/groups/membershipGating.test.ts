import { describe, expect, it } from "vitest";
import {
  hasPendingSettlements,
  canLeaveGroup,
  selfRole,
  canManageMembers,
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
