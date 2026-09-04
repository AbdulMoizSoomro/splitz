import type { User, Friendship } from "../../types/user";
import { MONEY_TOLERANCE, type Counterparty, type GroupRef } from "../balances/ledger";

export interface UnifiedConnection {
  userId: number;
  name: string;
  username: string;
  firstName: string;
  lastName: string;
  email?: string;
  isFriend: boolean;
  isPendingOutgoing: boolean;
  balance: number;
  groups: GroupRef[];
}

export interface FriendsSummary {
  totalOwed: number;
  totalOwe: number;
  netBalance: number;
}

export type ConnectionFilter = "ALL" | "YOU_OWE" | "OWED_TO_YOU" | "SETTLED";

export interface DeriveConnectionsOptions {
  friends?: User[];
  counterparties?: Counterparty[];
  outgoingRequests?: Friendship[];
}

/**
 * Compute the 3-metric summary across all connections with non-zero balances.
 */
export function computeFriendsSummary(
  connections: UnifiedConnection[],
): FriendsSummary {
  let totalOwed = 0;
  let totalOwe = 0;

  for (const conn of connections) {
    if (conn.balance > MONEY_TOLERANCE) {
      totalOwed += conn.balance;
    } else if (conn.balance < -MONEY_TOLERANCE) {
      totalOwe += Math.abs(conn.balance);
    }
  }

  const netBalance = totalOwed - totalOwe;

  return {
    totalOwed: parseFloat(totalOwed.toFixed(2)),
    totalOwe: parseFloat(totalOwe.toFixed(2)),
    netBalance: parseFloat(netBalance.toFixed(2)),
  };
}

/**
 * Merges confirmed friends and counterparties into a unified list.
 */
export function deriveUnifiedConnections({
  friends = [],
  counterparties = [],
  outgoingRequests = [],
}: DeriveConnectionsOptions): UnifiedConnection[] {
  const friendMap = new Map<number, User>();
  friends.forEach((f) => friendMap.set(f.id, f));

  const counterpartyMap = new Map<number, Counterparty>();
  counterparties.forEach((cp) => counterpartyMap.set(cp.userId, cp));

  const pendingOutgoingSet = new Set<number>();
  outgoingRequests.forEach((req) => {
    if (req.addresseeId) pendingOutgoingSet.add(req.addresseeId);
    if (req.friendId) pendingOutgoingSet.add(req.friendId);
  });

  const connections: UnifiedConnection[] = [];
  const processedUserIds = new Set<number>();

  // 1. Process all confirmed friends
  for (const friend of friends) {
    processedUserIds.add(friend.id);
    const cp = counterpartyMap.get(friend.id);
    const fullName = [friend.firstName, friend.lastName].filter(Boolean).join(" ").trim();
    const name = fullName || friend.username || "";

    connections.push({
      userId: friend.id,
      name,
      username: friend.username || "",
      firstName: friend.firstName || "",
      lastName: friend.lastName || "",
      email: friend.email,
      isFriend: true,
      isPendingOutgoing: false,
      balance: cp?.balance ?? 0,
      groups: cp?.groups ?? [],
    });
  }

  // 2. Process counterparties who are not friends (only if active balance)
  for (const cp of counterparties) {
    if (processedUserIds.has(cp.userId)) continue;
    if (Math.abs(cp.balance) <= MONEY_TOLERANCE) continue;

    const fullName = [cp.firstName, cp.lastName].filter(Boolean).join(" ").trim();
    const name = fullName || cp.name || cp.username || `User #${cp.userId}`;

    connections.push({
      userId: cp.userId,
      name,
      username: cp.username || "",
      firstName: cp.firstName || "",
      lastName: cp.lastName || "",
      email: cp.email,
      isFriend: false,
      isPendingOutgoing: pendingOutgoingSet.has(cp.userId),
      balance: cp.balance,
      groups: cp.groups ?? [],
    });
  }

  return connections;
}

export interface FilterAndSortOptions {
  filter: ConnectionFilter;
  search: string;
}

/**
 * Filter and sort unified connections.
 * Sorting: Active balances first by absolute magnitude descending, then settled alphabetically.
 */
export function filterAndSortConnections(
  connections: UnifiedConnection[],
  { filter, search }: FilterAndSortOptions,
): UnifiedConnection[] {
  const normalizedSearch = search.trim().toLowerCase();

  return connections
    .filter((conn) => {
      // 1. Filter by status
      if (filter === "YOU_OWE" && conn.balance >= -MONEY_TOLERANCE) {
        return false;
      }
      if (filter === "OWED_TO_YOU" && conn.balance <= MONEY_TOLERANCE) {
        return false;
      }
      if (filter === "SETTLED" && Math.abs(conn.balance) > MONEY_TOLERANCE) {
        return false;
      }

      // 2. Filter by search query
      if (normalizedSearch) {
        const matchesName = conn.name.toLowerCase().includes(normalizedSearch);
        const matchesUsername = conn.username.toLowerCase().includes(normalizedSearch);
        const matchesGroup = conn.groups.some((g) =>
          g.name.toLowerCase().includes(normalizedSearch),
        );
        if (!matchesName && !matchesUsername && !matchesGroup) {
          return false;
        }
      }

      return true;
    })
    .sort((a, b) => {
      const aIsActive = Math.abs(a.balance) > MONEY_TOLERANCE;
      const bIsActive = Math.abs(b.balance) > MONEY_TOLERANCE;

      if (aIsActive && !bIsActive) return -1;
      if (!aIsActive && bIsActive) return 1;

      if (aIsActive && bIsActive) {
        // Higher absolute debt comes first
        return Math.abs(b.balance) - Math.abs(a.balance);
      }

      // Both settled: alphabetical by name
      return a.name.localeCompare(b.name);
    });
}
