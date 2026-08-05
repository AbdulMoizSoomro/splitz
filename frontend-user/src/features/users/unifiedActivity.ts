import type { Expense } from "../../types/expense";
import type { FriendshipSettlementDTO } from "../../types/user";

/**
 * The unified friend-activity feed, owned by one module.
 *
 * FriendDetailPage used to merge expenses and settlements and sort them into
 * a single timestamped feed inline inside a `useMemo` — pure logic trapped in
 * a 755-line view, unreachable by any sibling. This module is the small, pure
 * interface for that merge, so the feed is reusable and testable on its own.
 */

export type ActivityItem =
  | { type: "expense"; data: Expense; date: Date }
  | { type: "settlement"; data: FriendshipSettlementDTO; date: Date };

/**
 * Merge shared expenses and settlements into one feed, newest first.
 * An expense sorts by its `expenseDate`; a settlement by its `createdAt`.
 * Either side may be absent.
 */
export function mergeActivity(
  expenses: Expense[] | undefined,
  settlements: FriendshipSettlementDTO[] | undefined,
): ActivityItem[] {
  const items: ActivityItem[] = [];

  (expenses ?? []).forEach((e) => {
    items.push({ type: "expense", data: e, date: new Date(e.expenseDate) });
  });

  (settlements ?? []).forEach((s) => {
    items.push({ type: "settlement", data: s, date: new Date(s.createdAt) });
  });

  return items.sort((a, b) => b.date.getTime() - a.date.getTime());
}
