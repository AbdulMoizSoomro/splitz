import { useMemo } from "react";
import {
  useQuery,
  useQueries,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import { useAuthStore } from "../store/authStore";
import { friendService } from "../features/users/friendService";
import { userService } from "../features/users/userService";
import { queryKeys } from "../lib/queryKeys";
import type { User } from "../types/user";

function formatName(
  user?: Partial<User> | { firstName?: string; lastName?: string; username?: string } | null,
): string | null {
  if (!user) return null;
  const fullName = `${user.firstName ?? ""} ${user.lastName ?? ""}`.trim();
  if (fullName) return fullName;
  if (user.username) return user.username;
  return null;
}

function lookupCachedName(id: number, queryClient: QueryClient): string | null {
  // Direct user cache
  const cachedUser = queryClient.getQueryData<User>(queryKeys.user(id));
  if (cachedUser) {
    const name = formatName(cachedUser);
    if (name) return name;
  }
  return null;
}

/**
 * Bulk variant: resolves a list of userIds to a `Record<number, string>`.
 * Uses cached friends, cached query data (e.g. active group balances), and falls back to lightweight user lookups.
 * Completely decoupled from multi-group financial ledger calculations.
 */
export function useDisplayNames(userIds: number[]): Record<number, string> {
  const user = useAuthStore((state) => state.user);
  const currentUserId = Number(user?.id);
  const queryClient = useQueryClient();

  // Load current user's friends list
  const { data: friends } = useQuery({
    queryKey: queryKeys.friends(currentUserId),
    queryFn: () => friendService.getFriends(currentUserId),
    enabled: !!currentUserId,
  });

  // Unique, positive external user IDs
  const otherIds = useMemo(() => {
    const unique = Array.from(new Set(userIds)).filter(
      (id) => id && id !== currentUserId,
    );
    return unique;
  }, [userIds, currentUserId]);

  // Build friend map
  const friendMap = useMemo(() => {
    const map = new Map<number, string>();
    (friends ?? []).forEach((f) => {
      const name = formatName(f);
      if (name) map.set(f.id, name);
    });
    return map;
  }, [friends]);

  // Check cache for non-friend IDs
  const cachedNameMap = useMemo(() => {
    const map = new Map<number, string>();
    otherIds.forEach((id) => {
      if (!friendMap.has(id)) {
        const cached = lookupCachedName(id, queryClient);
        if (cached) map.set(id, cached);
      }
    });
    return map;
  }, [otherIds, friendMap, queryClient]);

  // For IDs not in friendMap or cached queries, fetch via userService
  const missingIds = useMemo(() => {
    return otherIds.filter(
      (id) => !friendMap.has(id) && !cachedNameMap.has(id),
    );
  }, [otherIds, friendMap, cachedNameMap]);

  const userQueries = useQueries({
    queries: missingIds.map((id) => ({
      queryKey: queryKeys.user(id),
      queryFn: () => userService.getUser(id),
      staleTime: 5 * 60 * 1000,
      enabled: missingIds.length > 0,
    })),
  });

  const resolvedNameMap = useMemo(() => {
    const map = new Map<number, string>();

    // 1. Friends
    friendMap.forEach((name, id) => map.set(id, name));

    // 2. Cached query data (e.g. active group balance sheet)
    cachedNameMap.forEach((name, id) => map.set(id, name));

    // 3. Fetched users
    userQueries.forEach((q, idx) => {
      const id = missingIds[idx];
      const name = formatName(q.data);
      if (name) {
        map.set(id, name);
      }
    });

    return map;
  }, [friendMap, cachedNameMap, userQueries, missingIds]);

  return useMemo(() => {
    const result: Record<number, string> = {};
    userIds.forEach((id) => {
      if (id === currentUserId) {
        result[id] = "You";
      } else {
        result[id] = resolvedNameMap.get(id) ?? `User ${id}`;
      }
    });
    return result;
  }, [userIds, currentUserId, resolvedNameMap]);
}

/**
 * Resolves a single userId to a display name.
 * Self → "You", friends/users → "First Last", unknown → "User N".
 */
export function useDisplayName(userId: number): string {
  const ids = useMemo(() => [userId], [userId]);
  const map = useDisplayNames(ids);
  return map[userId] ?? (userId === 0 ? "Unknown" : `User ${userId}`);
}