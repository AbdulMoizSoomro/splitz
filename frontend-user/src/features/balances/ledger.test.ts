import { describe, it, expect } from "vitest";
import {
  MONEY_TOLERANCE,
  deriveLedger,
  deriveCounterparties,
  decomposePosition,
  balanceInGroup,
  memberNames,
  userPositionSource,
  type GroupBalanceSlice,
} from "./ledger";
import type { Balance, Debt } from "../../types/group";

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

function slice(
  over: Partial<GroupBalanceSlice> & { groupId: number },
): GroupBalanceSlice {
  return { groupName: `Group ${over.groupId}`, balances: [], simplifiedDebts: [], ...over };
}

function debt(over: Partial<Debt> & { from: number; to: number; amount: number }): Debt {
  return { fromUsername: undefined, toUsername: undefined, ...over };
}

// ---------------------------------------------------------------------------
// deriveCounterparties
// ---------------------------------------------------------------------------
describe("ledger — deriveCounterparties", () => {
  it("aggregates one counterparty across two groups into a single position", () => {
    const slices = [
      slice({
        groupId: 10,
        groupName: "Trip",
        simplifiedDebts: [debt({ from: 2, to: 1, amount: 15 })],
      }),
      slice({
        groupId: 20,
        groupName: "Flat",
        simplifiedDebts: [debt({ from: 1, to: 2, amount: 5 })],
      }),
    ];

    const result = deriveCounterparties(1, slices);

    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      userId: 2,
      balance: 10, // they owe 15 in Trip, we owe 5 in Flat → 10
      groups: [
        { id: 10, name: "Trip" },
        { id: 20, name: "Flat" },
      ],
    });
  });

  it("uses the sign convention: from == key is negative, to == key is positive", () => {
    const slices = [
      slice({
        groupId: 10,
        simplifiedDebts: [
          debt({ from: 1, to: 2, amount: 25 }), // key owes Bob
          debt({ from: 3, to: 1, amount: 40 }), // Carol owes key
        ],
      }),
    ];

    const result = deriveCounterparties(1, slices);

    const byId = Object.fromEntries(result.map((cp) => [cp.userId, cp]));
    expect(byId[2].balance).toBe(-25);
    expect(byId[3].balance).toBe(40);
  });

  it("skips debts that do not involve the key", () => {
    const slices = [
      slice({
        groupId: 10,
        simplifiedDebts: [debt({ from: 2, to: 3, amount: 12 })],
      }),
    ];

    expect(deriveCounterparties(1, slices)).toEqual([]);
  });

  it("resolves names from the member rows, falling back to debt usernames", () => {
    const slices = [
      slice({
        groupId: 10,
        balances: TRIO,
        simplifiedDebts: [debt({ from: 2, to: 1, amount: 5 })],
      }),
      slice({
        groupId: 20,
        simplifiedDebts: [debt({ from: 1, to: 4, amount: 3, toUsername: "dave" })],
      }),
    ];

    const result = deriveCounterparties(1, slices);

    const byId = Object.fromEntries(result.map((cp) => [cp.userId, cp]));
    expect(byId[2].name).toBe("Bob Smith"); // from the member row
    expect(byId[4].name).toBe("dave"); // from the debt username
  });
});

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
    const slices = [
      slice({
        groupId: 10,
        groupName: "Trip",
        balances: TRIO,
        simplifiedDebts: [debt({ from: 2, to: 1, amount: 20 })],
      }),
      slice({ groupId: 20, groupName: "Flat", balances: TRIO }),
    ];

    const ledger = deriveLedger(1, source, slices);

    expect(ledger.key).toBe(1);
    expect(ledger.counterparties).toEqual([
      { userId: 2, name: "Bob Smith", balance: 20, groups: [{ id: 10, name: "Trip" }] },
    ]);
    expect(ledger.memberNames).toEqual({
      1: "Alice Green",
      2: "Bob Smith",
      3: "Carol Jones",
    });
    expect(ledger.position).toEqual({ total: 50, groupTotal: 20, direct: 30 });
    expect(ledger.groupPositions).toEqual([
      { groupId: 10, groupName: "Trip", balance: 20 },
      { groupId: 20, groupName: "Flat", balance: 0 },
    ]);
  });

  it("returns an empty ledger when there is no source and no slices", () => {
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

describe("ledger — memberNames", () => {
  it("prefers member rows over debt usernames", () => {
    const names = memberNames([
      slice({
        groupId: 10,
        balances: TRIO,
        simplifiedDebts: [
          debt({ from: 2, to: 3, amount: 1, fromUsername: "bobby", toUsername: "carolj" }),
        ],
      }),
    ]);

    expect(names[2]).toBe("Bob Smith");
    expect(names[3]).toBe("Carol Jones");
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
