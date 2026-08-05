import { describe, expect, it, vi } from "vitest";
import { queryKeys, invalidations, bindInvalidations } from "./queryKeys";

describe("queryKeys", () => {
  it("group-scoped keys carry the group id", () => {
    expect(queryKeys.groups()).toEqual(["groups"]);
    expect(queryKeys.group(7)).toEqual(["group", 7]);
    expect(queryKeys.groupBalances(9)).toEqual(["group-balances", 9]);
    expect(queryKeys.groupSettlements(3)).toEqual(["group-settlements", 3]);
    expect(queryKeys.groupActivity(4)).toEqual(["group-activity", 4]);
    expect(queryKeys.expenses(5)).toEqual(["expenses", 5]);
    expect(queryKeys.categories()).toEqual(["categories"]);
  });

  it("friend-scoped keys carry the user ids", () => {
    expect(queryKeys.friends(1)).toEqual(["friends", 1]);
    expect(queryKeys.friendRequests(1)).toEqual(["friend-requests", 1]);
    expect(queryKeys.friendRequests(1, "OUTGOING")).toEqual([
      "friend-requests",
      1,
      "OUTGOING",
    ]);
    expect(queryKeys.friendBalance(1, 2)).toEqual(["friend-balance", 1, 2]);
    expect(queryKeys.friendSettlements(1, 2)).toEqual([
      "friend-settlements",
      1,
      2,
    ]);
    expect(queryKeys.userBalances(1)).toEqual(["user-balances", 1]);
    expect(queryKeys.tempFriends(1)).toEqual(["temp-friends", 1]);
    expect(queryKeys.userSearch("bo")).toEqual(["users", "search", "bo"]);
  });

  it("user-singleton key carries the id", () => {
    expect(queryKeys.user(42)).toEqual(["users", 42]);
    expect(queryKeys.userSearch("bo")).toEqual(["users", "search", "bo"]);
  });

  it("simplification keys carry the group id", () => {
    expect(queryKeys.simplificationPlan(6)).toEqual([
      "group-simplification-plan",
      6,
    ]);
    expect(queryKeys.simplificationSettings(6)).toEqual([
      "group-simplification-settings",
      6,
    ]);
  });

  it("shared-expenses key carries the friend id and the group ids", () => {
    expect(queryKeys.sharedExpenses(2, [3, 4])).toEqual([
      "shared-expenses",
      2,
      [3, 4],
    ]);
  });
});

describe("invalidations (the keys a mutation must refresh)", () => {
  it("expenseMutated refreshes the list, the activity feed and the balances", () => {
    const invalidate = vi.fn();
    invalidations.expenseMutated(invalidate, 9);
    expect(invalidate).toHaveBeenCalledWith(["expenses", 9]);
    expect(invalidate).toHaveBeenCalledWith(["group-activity", 9]);
    expect(invalidate).toHaveBeenCalledWith(["group-balances", 9]);
    expect(invalidate).toHaveBeenCalledTimes(3);
  });

  it("friendshipMutated refreshes friends, temp friends, user balances and friend requests", () => {
    const invalidate = vi.fn();
    invalidations.friendshipMutated(invalidate, 1);
    expect(invalidate).toHaveBeenCalledWith(["friends", 1]);
    expect(invalidate).toHaveBeenCalledWith(["temp-friends", 1]);
    expect(invalidate).toHaveBeenCalledWith(["user-balances", 1]);
    expect(invalidate).toHaveBeenCalledWith(["friend-requests", 1]);
    expect(invalidate).toHaveBeenCalledTimes(4);
  });

  it("settlementMutated refreshes the friend balance, settlements and balances", () => {
    const invalidate = vi.fn();
    invalidations.settlementMutated(invalidate, 1, 2);
    expect(invalidate).toHaveBeenCalledWith(["friend-balance", 1, 2]);
    expect(invalidate).toHaveBeenCalledWith(["friend-settlements", 1, 2]);
    expect(invalidate).toHaveBeenCalledWith(["user-balances", 1]);
    expect(invalidate).toHaveBeenCalledTimes(3);
  });
});

describe("bindInvalidations", () => {
  it("turns a react-query client into the plain-callback form invalidations expect", () => {
    const invalidateQueries = vi.fn();
    const invalidate = bindInvalidations({ invalidateQueries } as never);
    invalidate(["expenses", 5]);
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["expenses", 5] });
  });
});
