import { describe, it, expect } from "vitest";
import {
  MONEY_TOLERANCE,
  deriveLedger,
  decomposePosition,
  balanceInGroup,
  userPositionSource,
  type Counterparty,
} from "./ledger";
import type { Balance } from "../../types/group";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------
const TRIO: Balance[] = [
  {
    userId: 1,
    username: "alice",
    email: "alice@example.com",
    firstName: "Alice",
    lastName: "Green",
    balance: 20,
  },
  {
    userId: 2,
    username: "bob",
    email: "bob@example.com",
    firstName: "Bob",
    lastName: "Smith",
    balance: -20,
  },
  {
    userId: 3,
    username: "carol",
    email: "carol@example.com",
    firstName: "Carol",
    lastName: "Jones",
    balance: 0,
  },
];

// ---------------------------------------------------------------------------
// decomposePosition / deriveLedger
// ---------------------------------------------------------------------------
describe("ledger — decomposePosition", () => {
  it("computes direct = total − groupTotal", () => {
    const { position, groupPositions } = decomposePosition({
      total: 55,
      balances: [
        { groupId: 10, groupName: "Trip", balance: 20 },
        { groupId: 20, groupName: "Flat", balance: 5 },
      ],
    });

    expect(position).toEqual({ total: 55, groupTotal: 25, direct: 30 });
    expect(groupPositions).toHaveLength(2);
  });

  it("treats a missing group list as zero group total", () => {
    const { position } = decomposePosition({ total: -12 });
    expect(position).toEqual({ total: -12, groupTotal: 0, direct: -12 });
  });
});

describe("ledger — deriveLedger", () => {
  it("assembles counterparties, member names, positions and the position", () => {
    const source = userPositionSource({
      totalBalance: 50,
      groupBalances: [
        { groupId: 10, groupName: "Trip", balance: 20 },
        { groupId: 20, groupName: "Flat", balance: 0 },
      ],
    });
    const counterparties: Counterparty[] = [
      {
        userId: 2,
        name: "Bob Smith",
        firstName: "Bob",
        lastName: "Smith",
        username: "bob",
        balance: 20,
        groups: [{ id: 10, name: "Trip" }],
      },
    ];

    const ledger = deriveLedger(1, source, counterparties);

    expect(ledger.key).toBe(1);
    expect(ledger.counterparties).toEqual([
      {
        userId: 2,
        name: "Bob Smith",
        firstName: "Bob",
        lastName: "Smith",
        username: "bob",
        balance: 20,
        groups: [{ id: 10, name: "Trip" }],
      },
    ]);
    expect(ledger.memberNames).toEqual({
      2: "Bob Smith",
    });
    expect(ledger.position).toEqual({ total: 50, groupTotal: 20, direct: 30 });
    expect(ledger.groupPositions).toEqual([
      { groupId: 10, groupName: "Trip", balance: 20 },
      { groupId: 20, groupName: "Flat", balance: 0 },
    ]);
  });

  it("returns an empty ledger when there is no source and no counterparties", () => {
    const ledger = deriveLedger(1, undefined, []);

    expect(ledger.counterparties).toEqual([]);
    expect(ledger.memberNames).toEqual({});
    expect(ledger.position).toEqual({ total: 0, groupTotal: 0, direct: 0 });
  });
});

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------
describe("ledger — balanceInGroup", () => {
  it("returns the signed balance of a member, or zero when absent", () => {
    expect(balanceInGroup(TRIO, 2)).toBe(-20);
    expect(balanceInGroup(TRIO, 99)).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// Contract notes for consumers
// ---------------------------------------------------------------------------
describe("ledger — tolerance contract", () => {
  it("exposes the money tolerance consumers use for threshold filtering", () => {
    expect(MONEY_TOLERANCE).toBe(0.01);
  });
});
