import { describe, it, expect } from "vitest";
import {
  combineFeed,
  applyFilter,
  bucketLabelFor,
  bucketize,
  buildCardView,
  type ActivityEntry,
} from "./activityStreamEngine";
import type { Expense } from "../../types/expense";
import type { Settlement } from "../balances/settlementService";

function expense(
  id: number,
  date: string,
  overrides: Partial<Expense> = {},
): Expense {
  return {
    id,
    groupId: 1,
    description: "Dinner",
    amount: 100,
    currency: "USD",
    paidBy: 1,
    expenseDate: date,
    splits: [],
    createdAt: date,
    updatedAt: date,
    ...overrides,
  };
}

function settlement(
  id: number,
  date: string,
  overrides: Partial<Settlement> = {},
): Settlement {
  return {
    id,
    payerId: 1,
    payeeId: 2,
    amount: 50,
    currency: "USD",
    groupId: 1,
    status: "COMPLETED",
    createdAt: date,
    updatedAt: date,
    ...overrides,
  };
}

const resolver = {
  nameOf: (userId: number) => (userId === 1 ? "Alice" : `User ${userId}`),
  groupNameOf: (groupId: number) =>
    groupId === 1 ? "Ski" : `Group #${groupId}`,
};

const NOW = new Date();

/** ISO date at local noon for the day at the given offset from today. */
function dayOffset(days: number): string {
  const d = new Date(NOW);
  d.setDate(NOW.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}T12:00:00`;
}

describe("combineFeed", () => {
  it("merges expenses and settlements into one typed, newest-first-sorted feed", () => {
    const feed = combineFeed(
      [
        expense(2, "2026-01-05T00:00:00.000Z"),
        expense(1, "2026-01-03T00:00:00.000Z"),
      ],
      [settlement(3, "2026-01-04T00:00:00.000Z")],
    );

    expect(feed.map((e) => `${e.type}:${e.id}` as const)).toEqual([
      "EXPENSE:2",
      "SETTLEMENT:3",
      "EXPENSE:1",
    ]);
  });

  it("keeps both sides when one is missing", () => {
    expect(
      combineFeed([expense(1, "2026-01-03T00:00:00.000Z")], undefined),
    ).toHaveLength(1);
    expect(
      combineFeed(undefined, [settlement(3, "2026-01-04T00:00:00.000Z")]),
    ).toHaveLength(1);
    expect(combineFeed(undefined, undefined)).toEqual([]);
  });

  it("picks the raw date via fallback chain for settlements", () => {
    const feed = combineFeed([] as Expense[], [
      settlement(3, "2026-01-04T00:00:00.000Z", {
        settledAt: "2026-01-05T00:00:00.000Z",
      }),
    ]);
    expect(feed[0].date).toBe("2026-01-05T00:00:00.000Z");
  });

  it("falls back to createdAt for expenses missing expenseDate", () => {
    const e = expense(1, "2026-01-03T00:00:00.000Z");
    delete (e as Partial<Expense>).expenseDate;
    const feed = combineFeed([e], undefined);
    expect(feed[0].date).toBe("2026-01-03T00:00:00.000Z");
  });

  it("sorts entries with unparseable dates last without NaN breakdowns", () => {
    const bad = expense(1, "2026-01-03T00:00:00.000Z");
    (bad as Partial<Expense>).expenseDate = "not-a-date";
    const feed = combineFeed(
      [
        { ...bad, id: 1, createdAt: "not-a-date" },
        expense(2, "2026-01-05T00:00:00.000Z"),
        { ...bad, id: 3, createdAt: "also-invalid" },
      ],
      undefined,
    );
    expect(feed.map((e) => e.id)).toEqual([2, 1, 3]);
  });
});

describe("applyFiltering", () => {
  const feed: ActivityEntry[] = combineFeed(
    [expense(1, "2026-01-03T00:00:00.000Z")],
    [settlement(3, "2026-01-04T00:00:00.000Z")],
  );

  it("keeps everything for ALL", () => {
    expect(applyFilter(feed, "ALL")).toHaveLength(2);
  });

  it("keeps only type-typed entries", () => {
    const expenses = applyFilter(feed, "EXPENSE");
    const sets = applyFilter(feed, "SETTLEMENT");
    expect(expenses).toHaveLength(1);
    expect(expenses[0].type).toBe("EXPENSE");
    expect(sets).toHaveLength(1);
    expect(sets[0].type).toBe("SETTLEMENT");
  });
});

describe("date bucketing", () => {
  it("labels Today for the same calendar day", () => {
    expect(bucketLabelFor(dayOffset(0), NOW)).toBe("Today");
  });

  it("labels the previous calendar day as Yesterday", () => {
    expect(bucketLabelFor(dayOffset(-1), NOW)).toBe("Yesterday");
  });

  it("labels earlier dates with a calendar label", () => {
    const threeDaysAgo = new Date(NOW);
    threeDaysAgo.setDate(NOW.getDate() - 3);
    const month = threeDaysAgo.toLocaleString("en-US", { month: "short" });
    expect(bucketLabelFor(dayOffset(-3), NOW)).toBe(
      `${month} ${threeDaysAgo.getDate()}, ${threeDaysAgo.getFullYear()}`,
    );
  });

  it("renders an unknown/empty date as Unknown", () => {
    expect(bucketLabelFor("", NOW)).toBe("Unknown");
  });

  it("groups the feed into ordered buckets preserving per-bucket [ids]", () => {
    const buckets = bucketize(
      combineFeed(
        [expense(1, dayOffset(-3)), expense(2, dayOffset(0))],
        [settlement(3, dayOffset(-1))],
      ),
      NOW,
    );

    const threeDaysAgo = new Date(NOW);
    threeDaysAgo.setDate(NOW.getDate() - 3);
    const month = threeDaysAgo.toLocaleString("en-US", { month: "short" });
    expect(buckets.map((b) => b.label)).toEqual([
      "Today",
      "Yesterday",
      `${month} ${threeDaysAgo.getDate()}, ${threeDaysAgo.getFullYear()}`,
    ]);
    expect(buckets.map((b) => b.items.map((e) => e.id))).toEqual([[2], [3], [1]]);
  });
});

// Local helper: build a single feed entry for card tests
function entry(e: Expense): ActivityEntry {
  return combineFeed([e], undefined)[0];
}

function entryS(s: Settlement): ActivityEntry {
  return combineFeed(undefined, [s])[0];
}

describe("buildCardView (expenses)", () => {
  it("builds the payer perspective (payer is self)", () => {
    const card = buildCardView(
      entry(expense(1, "2026-01-05T00:00:00.000Z", { paidBy: 1 })),
      1,
      resolver,
    );
    expect(card.title).toBe('You paid for "Dinner"');
    expect(card.amountLabel).toBe("+ $100.00");
    expect(card.amountTone).toBe("positive");
    expect(card.variant).toBe("expense-payer");
    expect(card.footerLabel).toBe("Total Expense: $100.00");
    expect(card.group?.name).toBe("Ski");
  });

  it("marks the user as owing when payer is someone else but a split exists", () => {
    const card = buildCardView(
      entry(
        expense(1, "2026-01-05T00:00:00.000Z", {
          paidBy: 2,
          splits: [{ id: 1, userId: 1, shareAmount: 40 }],
        }),
      ),
      1,
      resolver,
    );
    expect(card.title).toBe('User 2 paid for "Dinner"');
    expect(card.amountLabel).toBe("- $40.00");
    expect(card.amountTone).toBe("negative");
    expect(card.variant).toBe("expense-owe");
  });

  it("marks an unrelated expense as not involved", () => {
    const card = buildCardView(
      entry(expense(1, "2026-01-05T00:00:00.000Z", { paidBy: 2, splits: [] })),
      1,
      resolver,
    );
    expect(card.amountLabel).toBe("$0.00");
    expect(card.amountTone).toBe("neutral");
    expect(card.variant).toBe("expense-uninvolved");
  });

  it("falls back to a group label when the group is unknown", () => {
    const card = buildCardView(
      entry(expense(1, "2026-01-05T00:00:00.000Z", { groupId: 99 })),
      1,
      resolver,
    );
    expect(card.group?.name).toBe("Group #99");
  });
});

describe("buildCardView (settlements)", () => {
  it("builds the payer perspective (payer is self)", () => {
    const card = buildCardView(entryS(settlement(1, "2026-01-05T00:00:00.000Z")), 1, resolver);
    expect(card.title).toBe("You sent a payment to User 2");
    expect(card.amountLabel).toBe("+ $50.00");
    expect(card.variant).toBe("settlement-payer");
  });

  it("builds the payee perspective (payee is self)", () => {
    const s = settlement(1, "2026-01-05T00:00:00.000Z", { payerId: 2, payeeId: 1 });
    const card = buildCardView(entryS(s), 1, resolver);
    expect(card.title).toBe("User 2 sent you a payment");
    expect(card.amountLabel).toBe("-$50.00");
    expect(card.variant).toBe("settlement-payee");
  });

  it("renders a direct settlement with a Direct badge (no group)", () => {
    const s = settlement(1, "2026-01-05T00:00:00.000Z") as Settlement;
    delete (s as Partial<Settlement>).groupId;
    const card = buildCardView(entryS(s), 1, resolver);
    expect(card.group).toBeUndefined();
    expect(card.isDirect).toBe(true);
  });

  it("uses the settlement status", () => {
    const card = buildCardView(
      entryS(settlement(1, "2026-01-05T00:00:00.000Z", { status: "PENDING" })),
      1,
      resolver,
    );
    expect(card.status).toBe("PENDING");
  });
});