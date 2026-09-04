import { describe, it, expect } from "vitest";
import {
  computeFriendsSummary,
  deriveUnifiedConnections,
  filterAndSortConnections,
  type UnifiedConnection,
} from "./friendsLedger";
import type { User, Friendship } from "../../types/user";
import type { Counterparty } from "../balances/ledger";

describe("friendsLedger", () => {
  describe("computeFriendsSummary", () => {
    it("correctly aggregates total owed, total owe, and net balance", () => {
      const connections: Partial<UnifiedConnection>[] = [
        { userId: 1, balance: 50.0 }, // owes user $50
        { userId: 2, balance: -30.0 }, // user owes $30
        { userId: 3, balance: 25.5 }, // owes user $25.50
        { userId: 4, balance: 0.0 }, // settled
      ];

      const summary = computeFriendsSummary(connections as UnifiedConnection[]);

      expect(summary.totalOwed).toBeCloseTo(75.5, 2);
      expect(summary.totalOwe).toBeCloseTo(30.0, 2);
      expect(summary.netBalance).toBeCloseTo(45.5, 2);
    });

    it("handles empty connections array", () => {
      const summary = computeFriendsSummary([]);
      expect(summary.totalOwed).toBe(0);
      expect(summary.totalOwe).toBe(0);
      expect(summary.netBalance).toBe(0);
    });
  });

  describe("deriveUnifiedConnections", () => {
    const friend1: User = {
      id: 101,
      username: "alice",
      firstName: "Alice",
      lastName: "Smith",
      email: "alice@example.com",
    };
    const friend2: User = {
      id: 102,
      username: "bob",
      firstName: "Bob",
      lastName: "Jones",
      email: "bob@example.com",
    };

    const counterpartyAlice: Counterparty = {
      userId: 101,
      name: "Alice Smith",
      username: "alice",
      balance: 45.0,
      groups: [{ id: 1, name: "Ski Trip" }],
    };

    const counterpartyCharlie: Counterparty = {
      userId: 103,
      name: "Charlie Brown",
      username: "charlie",
      balance: -20.0,
      groups: [{ id: 2, name: "Apartment" }],
    };

    const counterpartyZero: Counterparty = {
      userId: 104,
      name: "Dave Zero",
      username: "dave",
      balance: 0.0,
      groups: [{ id: 1, name: "Ski Trip" }],
    };

    const outgoingReq: Friendship = {
      id: 1,
      requesterId: 999,
      addresseeId: 103,
      status: "PENDING",
      createdAt: "2026-09-01T00:00:00Z",
      updatedAt: "2026-09-01T00:00:00Z",
    };

    it("merges friends and counterparties with proper badges and status", () => {
      const result = deriveUnifiedConnections({
        friends: [friend1, friend2],
        counterparties: [counterpartyAlice, counterpartyCharlie, counterpartyZero],
        outgoingRequests: [outgoingReq],
      });

      // friend1: Alice (isFriend: true, balance: 45.0)
      const alice = result.find((c) => c.userId === 101);
      expect(alice).toBeDefined();
      expect(alice?.isFriend).toBe(true);
      expect(alice?.balance).toBe(45.0);
      expect(alice?.groups).toHaveLength(1);

      // friend2: Bob (isFriend: true, balance: 0 because not in counterparties)
      const bob = result.find((c) => c.userId === 102);
      expect(bob).toBeDefined();
      expect(bob?.isFriend).toBe(true);
      expect(bob?.balance).toBe(0.0);
      expect(bob?.groups).toHaveLength(0);

      // counterparty Charlie: Not a friend, balance -20.0, has pending outgoing request
      const charlie = result.find((c) => c.userId === 103);
      expect(charlie).toBeDefined();
      expect(charlie?.isFriend).toBe(false);
      expect(charlie?.isPendingOutgoing).toBe(true);
      expect(charlie?.balance).toBe(-20.0);

      // counterparty Dave: Not a friend, zero balance -> should NOT be included
      const dave = result.find((c) => c.userId === 104);
      expect(dave).toBeUndefined();
    });
  });

  describe("filterAndSortConnections", () => {
    const list: UnifiedConnection[] = [
      {
        userId: 1,
        name: "Bob Settled",
        username: "bob",
        firstName: "Bob",
        lastName: "Settled",
        isFriend: true,
        isPendingOutgoing: false,
        balance: 0,
        groups: [],
      },
      {
        userId: 2,
        name: "Alice Big Debt",
        username: "alice",
        firstName: "Alice",
        lastName: "Big Debt",
        isFriend: true,
        isPendingOutgoing: false,
        balance: -100,
        groups: [{ id: 1, name: "Apartment" }],
      },
      {
        userId: 3,
        name: "Charlie Small Debt",
        username: "charlie",
        firstName: "Charlie",
        lastName: "Small Debt",
        isFriend: false,
        isPendingOutgoing: false,
        balance: 25,
        groups: [{ id: 2, name: "Dinner" }],
      },
      {
        userId: 4,
        name: "Aaron Settled",
        username: "aaron",
        firstName: "Aaron",
        lastName: "Settled",
        isFriend: true,
        isPendingOutgoing: false,
        balance: 0,
        groups: [],
      },
    ];

    it("sorts active balances by absolute magnitude first, then settled alphabetically", () => {
      const sorted = filterAndSortConnections(list, { filter: "ALL", search: "" });
      expect(sorted.map((c) => c.userId)).toEqual([
        2, // |-100| = 100
        3, // |25| = 25
        4, // Aaron (settled, A before B)
        1, // Bob (settled)
      ]);
    });

    it("filters by YOU_OWE", () => {
      const filtered = filterAndSortConnections(list, {
        filter: "YOU_OWE",
        search: "",
      });
      expect(filtered.map((c) => c.userId)).toEqual([2]);
    });

    it("filters by OWED_TO_YOU", () => {
      const filtered = filterAndSortConnections(list, {
        filter: "OWED_TO_YOU",
        search: "",
      });
      expect(filtered.map((c) => c.userId)).toEqual([3]);
    });

    it("filters by SETTLED", () => {
      const filtered = filterAndSortConnections(list, {
        filter: "SETTLED",
        search: "",
      });
      expect(filtered.map((c) => c.userId)).toEqual([4, 1]);
    });

    it("filters by search string matching name or username", () => {
      const filtered = filterAndSortConnections(list, {
        filter: "ALL",
        search: "charlie",
      });
      expect(filtered.map((c) => c.userId)).toEqual([3]);
    });
  });
});
