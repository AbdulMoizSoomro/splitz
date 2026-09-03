import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { friendService } from "../features/users/friendService";
import { groupService } from "../features/groups/groupService";
import { useAuthStore } from "../store/authStore";
import {
  deriveLedger,
  userPositionSource,
  type Counterparty,
  type Ledger,
} from "../features/balances/ledger";
import type { Group } from "../types/group";
import type { User } from "../types/user";

// ---------------------------------------------------------------------------
// The ledger hook — one place owns the money-view data.
//
// Instead of fanning out N+1 queries across every group balance endpoint and
// running debt graph resolution on the client, this hook queries the backend's
// deep FinancialLedgerEngine endpoint (`GET /users/{id}/counterparties`) in a
// single round trip, unlocking counterparties and hydrated member names.
// ---------------------------------------------------------------------------

export interface UseLedgerOptions {
  /**
   * When true the ledger also loads the friends list and counterparties.
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

  const counterpartiesQuery = useQuery({
    queryKey: ["counterparties", currentUserId],
    queryFn: async () => (await groupService.getCounterparties(currentUserId)) ?? null,
    enabled: enabled && detail,
  });

  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: async () => (await groupService.getGroups()) ?? null,
  });

  const groups = groupsQuery.data ?? undefined;

  const counterparties: Counterparty[] = useMemo(() => {
    if (!detail || !counterpartiesQuery.data) return [];
    return counterpartiesQuery.data.map((cp) => {
      const fullName = [cp.firstName, cp.lastName].filter(Boolean).join(" ").trim();
      const name = fullName || cp.username || "";
      return {
        userId: cp.userId,
        name,
        balance: cp.balance,
        groups: cp.groups,
        username: cp.username,
        firstName: cp.firstName,
        lastName: cp.lastName,
        email: cp.email,
      };
    });
  }, [detail, counterpartiesQuery.data]);

  const ledger: Ledger | undefined = useMemo(() => {
    if (!enabled) return undefined;
    return deriveLedger(
      currentUserId,
      userPositionSource(userBalancesQuery.data ?? undefined),
      counterparties,
    );
  }, [enabled, currentUserId, userBalancesQuery.data, counterparties]);

  const friends: User[] | undefined = detail
    ? friendsQuery.data ?? undefined
    : undefined;

  const hasBalances = userBalancesQuery.data !== undefined;
  const groupsLoading = groupsQuery.isLoading;
  const balancesLoading =
    userBalancesQuery.isLoading ||
    (detail && counterpartiesQuery.isLoading);
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
