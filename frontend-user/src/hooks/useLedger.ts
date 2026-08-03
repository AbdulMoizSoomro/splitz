import { useMemo } from "react";
import { useQuery, useQueries } from "@tanstack/react-query";
import { friendService } from "../features/users/friendService";
import { groupService } from "../features/groups/groupService";
import { useAuthStore } from "../store/authStore";
import {
  deriveLedger,
  userPositionSource,
  type GroupBalanceSlice,
  type Ledger,
} from "../features/balances/ledger";
import type { Group } from "../types/group";
import type { User } from "../types/user";

// ---------------------------------------------------------------------------
// The ledger hook — one place owns the money-view data.
//
// The per-group balance fan-out (the N+1) used to be re-implemented inside
// useTempFriends and useDisplayName, each with its own shape. This hook runs
// it once, behind the pure deriveLedger module, and hands out the derived
// ledger to every consumer.
//
// Query keys mirror the app's existing ones (["groups"], ["user-balances", id],
// ["group-balances", id], ["friends", id]) so other pages share the cache
// instead of double-fetching.
// ---------------------------------------------------------------------------

export interface UseLedgerOptions {
  /**
   * When true the ledger also loads the friends list and the per-group
   * balance fan-out, unlocking counterparties and member names.
   * Position-only consumers (e.g. the groups list) leave it off and pay a
   * single `user-balances` request.
   */
  detail?: boolean;
}

export function useLedger(options: UseLedgerOptions = {}): {
  key: number;
  groups: Group[] | undefined;
  friends: User[] | undefined;
  ledger: Ledger | undefined;
  /** Whether the user's net balances actually loaded (vs. still pending/failed). */
  hasBalances: boolean;
  groupsLoading: boolean;
  balancesLoading: boolean;
  friendsLoading: boolean;
  isLoading: boolean;
} {
  const { detail = false } = options;
  const user = useAuthStore((state) => state.user);
  const currentUserId = Number(user?.id);
  const enabled = !!currentUserId;

  const friendsQuery = useQuery({
    queryKey: ["friends", currentUserId],
    queryFn: async () => (await friendService.getFriends(currentUserId)) ?? null,
    enabled: enabled && detail,
  });

  const userBalancesQuery = useQuery({
    queryKey: ["user-balances", currentUserId],
    queryFn: async () => (await groupService.getUserBalances(currentUserId)) ?? null,
    enabled,
  });

  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: async () => (await groupService.getGroups()) ?? null,
  });

  const groups = groupsQuery.data ?? undefined;

  // Every group the user belongs to, sourced from both the groups list and the
  // user balances — each covers the gaps in the other.
  const groupIds = useMemo(() => {
    if (!detail) return [];
    const ids = new Set<number>();
    (groups ?? []).forEach((g) => ids.add(g.id));
    (userBalancesQuery.data?.groupBalances ?? []).forEach((gb) =>
      ids.add(gb.groupId),
    );
    return [...ids].sort((a, b) => a - b);
  }, [detail, groups, userBalancesQuery.data]);

  const groupNameById = useMemo(() => {
    const map: Record<number, string> = {};
    (groups ?? []).forEach((g) => {
      map[g.id] = g.name;
    });
    (userBalancesQuery.data?.groupBalances ?? []).forEach((gb) => {
      map[gb.groupId] = gb.groupName;
    });
    return map;
  }, [groups, userBalancesQuery.data]);

  const groupBalanceQueries = useQueries({
    queries: groupIds.map((groupId) => ({
      queryKey: ["group-balances", groupId],
      queryFn: async () => (await groupService.getBalances(groupId)) ?? null,
      enabled: groupIds.length > 0,
    })),
  });

  const slices: GroupBalanceSlice[] = useMemo(() => {
    const items: (GroupBalanceSlice | null)[] = groupBalanceQueries.map(
      (q, idx) => {
        const data = q.data;
        if (!data) return null;
        const groupId = groupIds[idx];
        return {
          groupId,
          groupName: groupNameById[groupId],
          balances: data.balances,
          simplifiedDebts: data.simplifiedDebts,
        } satisfies GroupBalanceSlice;
      },
    );
    return items.filter((s): s is GroupBalanceSlice => s !== null);
  }, [groupBalanceQueries, groupIds, groupNameById]);

  const ledger: Ledger | undefined = useMemo(() => {
    if (!enabled) return undefined;
    return deriveLedger(
      currentUserId,
      userPositionSource(userBalancesQuery.data ?? undefined),
      slices,
    );
  }, [enabled, currentUserId, userBalancesQuery.data, slices]);

  const friends: User[] | undefined = detail
    ? friendsQuery.data ?? undefined
    : undefined;

  const hasBalances = userBalancesQuery.data !== undefined;
  const groupsLoading = groupsQuery.isLoading;
  const balancesLoading =
    userBalancesQuery.isLoading ||
    (detail && groupBalanceQueries.some((q) => q.isLoading));
  const friendsLoading = detail && friendsQuery.isLoading;

  return {
    key: currentUserId,
    groups,
    friends,
    ledger,
    hasBalances,
    groupsLoading,
    balancesLoading,
    friendsLoading,
    isLoading: groupsLoading || balancesLoading || friendsLoading,
  };
}
