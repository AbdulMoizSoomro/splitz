import { describe, expect, it } from "vitest";
import { mergeActivity, type ActivityItem } from "./unifiedActivity";
import type { Expense } from "../../types/expense";
import type { FriendshipSettlementDTO } from "../../types/user";

function expense(id: number, expenseDate: string): Expense {
  return {
    id,
    groupId: 1,
    description: `expense ${id}`,
    amount: 10,
    currency: "USD",
    paidBy: 1,
    expenseDate,
    splits: [],
    createdAt: expenseDate,
    updatedAt: expenseDate,
  };
}

function settlement(id: number, createdAt: string): FriendshipSettlementDTO {
  return {
    id,
    payerId: 1,
    payeeId: 2,
    amount: 4,
    status: "COMPLETED",
    createdAt,
    updatedAt: createdAt,
  };
}

describe("mergeActivity", () => {
  it("merges expenses and settlements into one feed sorted newest-first", () => {
    const feed = mergeActivity(
      [expense(1, "2026-01-01T00:00:00.000Z"), expense(2, "2026-01-03T00:00:00.000Z")],
      [settlement(101, "2026-01-02T00:00:00.000Z")],
    );

    expect(feed.map((i) => `${i.type}:${i.data.id}` as const)).toEqual([
      "expense:2",
      "settlement:101",
      "expense:1",
    ]);
  });

  it("tags each item with its source type", () => {
    const feed = mergeActivity([expense(1, "2026-01-01T00:00:00.000Z")], [settlement(9, "2026-01-02T00:00:00.000Z")]);
    const e = feed.find((i) => i.data.id === 1);
    const s = feed.find((i) => i.data.id === 9);
    expect(e?.type).toBe("expense");
    expect(s?.type).toBe("settlement");
  });

  it("returns an empty list when both inputs are absent", () => {
    expect(mergeActivity(undefined, undefined)).toEqual([]);
    expect(mergeActivity([], [])).toEqual([]);
  });

  it("returns only the present side when the other is missing", () => {
    const onlyExpenses = mergeActivity([expense(1, "2026-01-01T00:00:00.000Z")], undefined);
    expect(onlyExpenses).toHaveLength(1);
    expect(onlyExpenses[0].type).toBe("expense");
  });

  it("parses the date onto each item", () => {
    const feed = mergeActivity([expense(7, "2026-02-02T00:00:00.000Z")], []);
    expect((feed[0] as ActivityItem & { data: Expense }).date.toISOString()).toBe(
      "2026-02-02T00:00:00.000Z",
    );
  });
});
