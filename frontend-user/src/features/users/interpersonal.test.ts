import { describe, it, expect } from "vitest";
import {
  deriveRelationshipStatus,
  autoAllocateSettlement,
  validateSettlementAllocation,
  buildSettlementPayload,
} from "./interpersonal";
import type { User, Friendship } from "../../types/user";

describe("interpersonal module", () => {
  describe("deriveRelationshipStatus", () => {
    const friend: User = {
      id: 10,
      username: "alice",
      email: "alice@example.com",
      firstName: "Alice",
      lastName: "Smith",
    };

    const outgoingReq: Friendship = {
      id: 101,
      requesterId: 1,
      addresseeId: 20,
      status: "PENDING",
      createdAt: "2026-08-01T00:00:00Z",
    };

    it("returns CONFIRMED_FRIEND when user is in friend list", () => {
      const status = deriveRelationshipStatus({
        targetUserId: 10,
        friends: [friend],
        outgoingRequests: [],
        incomingRequests: [],
        netBalance: 0,
      });
      expect(status).toBe("CONFIRMED_FRIEND");
    });

    it("returns PENDING_OUTGOING when outgoing request exists for target", () => {
      const status = deriveRelationshipStatus({
        targetUserId: 20,
        friends: [],
        outgoingRequests: [outgoingReq],
        incomingRequests: [],
        netBalance: 0,
      });
      expect(status).toBe("PENDING_OUTGOING");
    });

    it("returns TEMP_FRIEND when not a friend but non-zero net balance exists", () => {
      const status = deriveRelationshipStatus({
        targetUserId: 30,
        friends: [],
        outgoingRequests: [],
        incomingRequests: [],
        netBalance: -25.5,
      });
      expect(status).toBe("TEMP_FRIEND");
    });

    it("returns NONE when not a friend, no requests, and zero balance", () => {
      const status = deriveRelationshipStatus({
        targetUserId: 40,
        friends: [],
        outgoingRequests: [],
        incomingRequests: [],
        netBalance: 0,
      });
      expect(status).toBe("NONE");
    });
  });

  describe("autoAllocateSettlement", () => {
    const groupBalances = [
      { groupId: 1, groupName: "Vacation", balance: -50 }, // User owes $50
      { groupId: 2, groupName: "Groceries", balance: -30 }, // User owes $30
      { groupId: 3, groupName: "Rent", balance: 100 }, // Friend owes user $100
    ];

    it("automatically allocates PAY amount to groups where user owes money", () => {
      const allocations = autoAllocateSettlement({
        amount: 60,
        type: "PAY",
        groupBalances,
      });

      // Should allocate $50 to group 1, and remaining $10 to group 2
      expect(allocations).toEqual({
        1: 50,
        2: 10,
      });
    });

    it("automatically allocates RECEIVE amount to groups where friend owes user", () => {
      const allocations = autoAllocateSettlement({
        amount: 70,
        type: "RECEIVE",
        groupBalances,
      });

      // Should allocate $70 to group 3
      expect(allocations).toEqual({
        3: 70,
      });
    });

    it("allocates max up to total group balance owed", () => {
      const allocations = autoAllocateSettlement({
        amount: 200,
        type: "PAY",
        groupBalances,
      });

      // Total owed across groups for PAY is $50 + $30 = $80.
      expect(allocations).toEqual({
        1: 50,
        2: 30,
      });
    });
  });

  describe("validateSettlementAllocation", () => {
    it("validates equal total allocation and amount", () => {
      const result = validateSettlementAllocation({
        amount: 50,
        isAllocating: true,
        allocations: { 1: "30", 2: "20" },
      });

      expect(result.isValid).toBe(true);
      expect(result.totalAllocated).toBe(50);
      expect(result.difference).toBe(0);
    });

    it("invalidates mismatched allocation total", () => {
      const result = validateSettlementAllocation({
        amount: 50,
        isAllocating: true,
        allocations: { 1: "30" },
      });

      expect(result.isValid).toBe(false);
      expect(result.totalAllocated).toBe(30);
      expect(result.difference).toBe(-20);
    });

    it("returns valid when allocation is toggled off", () => {
      const result = validateSettlementAllocation({
        amount: 50,
        isAllocating: false,
        allocations: {},
      });

      expect(result.isValid).toBe(true);
    });
  });

  describe("buildSettlementPayload", () => {
    it("builds unallocated payload when allocation is off", () => {
      const payload = buildSettlementPayload({
        currentUserId: 1,
        friendId: 2,
        type: "PAY",
        amount: 50,
        isAllocating: false,
        allocations: {},
      });

      expect(payload).toEqual({
        payerId: 1,
        payeeId: 2,
        amount: 50,
      });
    });

    it("builds allocated payload when allocation is enabled", () => {
      const payload = buildSettlementPayload({
        currentUserId: 1,
        friendId: 2,
        type: "RECEIVE",
        amount: 80,
        isAllocating: true,
        allocations: { 10: "50.00", 20: "30.00", 30: "0" },
      });

      expect(payload).toEqual({
        payerId: 2,
        payeeId: 1,
        amount: 80,
        allocations: [
          { groupId: 10, amount: 50 },
          { groupId: 20, amount: 30 },
        ],
      });
    });
  });
});
