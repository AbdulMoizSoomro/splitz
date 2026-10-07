/**
 * The single module that owns every React Query key in the app.
 *
 * Before this module, cache coordination was by string-matching: 88
 * hand-typed `["expenses", group.id]` literals and 50 `invalidateQueries`
 * calls were smeared across ~15 files, coordinated only by convention
 * (`useLedger.ts` even conceded keys were shared "by convention"). Renaming
 * or retyping one silently broke the mutation→list invalidation at N call
 * sites.
 *
 * This module is "deep": a small surface (`queryKeys` factories + the
 * `invalidations` helpers that bundle what a mutation must refresh) gives
 * every read and every invalidation a single-source, typed key. Factories
 * return read-only tuples; invalidations take an `invalidate` callback so the
 * only behaviour to test (which keys) is pure, and `bindInvalidations` wires a
 * component's react-query client to that callback in one line.
 *
 * Ids are typed `number | string` because the app's `currentUser.id` is a JWT
 * string; keys only need a stable value to match against.
 */

/** A loosely-typed id — React Query keys only need a stable, comparable value. */
type Id = number | string;

export const queryKeys = {
  // ----- Group / group money views -----
  groups: () => ["groups"] as const,
  group: (id: Id) => ["group", id] as const,
  groupBalances: (groupId: Id) => ["group-balances", groupId] as const,
  groupSettlements: (groupId: Id) => ["group-settlements", groupId] as const,
  groupActivity: (groupId: Id) => ["group-activity", groupId] as const,
  expenses: (groupId: Id) => ["expenses", groupId] as const,
  categories: () => ["categories"] as const,

  // ----- Users / friendships / friendship money views -----
  user: (id: Id) => ["users", id] as const,
  friends: (userId: Id) => ["friends", userId] as const,
  friendRequests: (userId: Id, direction?: "INCOMING" | "OUTGOING") =>
    direction
      ? (["friend-requests", userId, direction] as const)
      : (["friend-requests", userId] as const),
  friendBalance: (userId: Id, friendId: Id) =>
    ["friend-balance", userId, friendId] as const,
  friendSettlements: (userId: Id, friendId: Id) =>
    ["friend-settlements", userId, friendId] as const,
  sharedExpenses: (friendId: Id, groupIds: number[]) =>
    ["shared-expenses", friendId, groupIds] as const,
  userBalances: (userId: Id) => ["user-balances", userId] as const,
  tempFriends: (userId: Id) => ["temp-friends", userId] as const,
  userSearch: (query: string) => ["users", "search", query] as const,

  // ----- Simplification governance -----
  simplificationPlan: (groupId: Id) =>
    ["group-simplification-plan", groupId] as const,
  simplificationSettings: (groupId: Id) =>
    ["group-simplification-settings", groupId] as const,
  simplificationPreference: ["simplification-preference", "me"] as const,
} as const;

/** The minimal `queryClient.invalidateQueries` surface this module needs. */
export type Invalidate = (key: readonly unknown[]) => void;

/**
 * Bind a react-query client to the plain-callback form `invalidations` takes,
 * so components stop re-declaring the same `invalidateQueries({ queryKey })`
 * wrapper at every call site.
 */
export function bindInvalidations(queryClient: {
  invalidateQueries: (opts: { queryKey: readonly unknown[] }) => void;
}): Invalidate {
  return (key: readonly unknown[]) =>
    queryClient.invalidateQueries({ queryKey: key });
}

/**
 * Bundles the keys each mutation class must refresh, so components stop
 * hand-rolling the same `invalidateQueries` triples.
 */
export const invalidations = {
  /** A group expense was created / updated / deleted. */
  expenseMutated(invalidate: Invalidate, groupId: Id): void {
    invalidate(queryKeys.expenses(groupId));
    invalidate(queryKeys.groupActivity(groupId));
    invalidate(queryKeys.groupBalances(groupId));
  },

  /** A friendship (or friend request) edge changed for `userId`. */
  friendshipMutated(invalidate: Invalidate, userId: Id): void {
    invalidate(queryKeys.friends(userId));
    invalidate(queryKeys.tempFriends(userId));
    invalidate(queryKeys.userBalances(userId));
    invalidate(queryKeys.friendRequests(userId));
  },

  /** A settlement between `currentUserId ↔ friendId` was recorded/updated/confirmed. */
  settlementMutated(invalidate: Invalidate, currentUserId: Id, friendId: Id): void {
    invalidate(queryKeys.friendBalance(currentUserId, friendId));
    invalidate(queryKeys.friendSettlements(currentUserId, friendId));
    invalidate(queryKeys.userBalances(currentUserId));
  },
};

