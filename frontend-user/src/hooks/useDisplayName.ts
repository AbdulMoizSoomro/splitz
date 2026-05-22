import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuthStore } from "../store/authStore";
import { friendService } from "../features/users/friendService";
import { groupService } from "../features/groups/groupService";

// ---------------------------------------------------------------------------
// Internal hook — shared by useDisplayName and useDisplayNames.
// React Query deduplicates the underlying requests when both are mounted in
// the same tree, so there is no double-fetching cost.
//
// Resolution order:
//   1. friends  → "First Last"
//   2. group balance members (covers Temp Friends in group context) → "First Last"
//
// Self-detection ("You") and the numeric fallback are applied by the callers
// so this map only contains *other* users.
// ---------------------------------------------------------------------------
function useNameMap(currentUserId: number): Record<number, string> {
  // Source 1: friends
  const { data: friends = [] } = useQuery({
    queryKey: ["friends", currentUserId],
    queryFn: () => friendService.getFriends(currentUserId),
    enabled: !!currentUserId,
  });

  // Source 2: all groups → group balance members
  const { data: groups = [] } = useQuery({
    queryKey: ["groups"],
    queryFn: () => groupService.getGroups(),
    enabled: !!currentUserId,
  });

  const groupIds = useMemo(() => groups.map((g) => g.id), [groups]);

  // Fetch balances for every group the user is in (gives us names for all members)
  const { data: groupBalancesMap = {} } = useQuery({
    queryKey: ["display-name-group-balances", groupIds],
    queryFn: async () => {
      const map: Record<number, string> = {};
      await Promise.all(
        groupIds.map(async (id) => {
          try {
            const br = await groupService.getBalances(id);
            br.balances.forEach((b) => {
              const fullName = `${b.firstName} ${b.lastName}`.trim();
              if (fullName && b.userId !== currentUserId) {
                map[b.userId] = fullName;
              }
            });
          } catch {
            // ignore individual group failures
          }
        }),
      );
      return map;
    },
    enabled: groupIds.length > 0,
  });

  return useMemo(() => {
    const map: Record<number, string> = {};

    // Friends
    friends.forEach((f) => {
      const fullName = `${f.firstName} ${f.lastName}`.trim();
      if (fullName) map[f.id] = fullName;
    });

    // Group balance members (only set if not already resolved via friends)
    Object.entries(groupBalancesMap).forEach(([idStr, name]) => {
      const id = Number(idStr);
      if (!map[id]) map[id] = name;
    });

    return map;
  }, [friends, groupBalancesMap]);
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Resolves a single userId to a display name.
 * Self → "You", friends/group members → "First Last", unknown → "User N".
 */
export function useDisplayName(userId: number): string {
  const currentUserId = Number(useAuthStore((s) => s.user?.id) ?? 0);
  const nameMap = useNameMap(currentUserId);

  if (userId === currentUserId) return "You";
  return nameMap[userId] ?? `User ${userId}`;
}

/**
 * Bulk variant: resolves a list of userIds to a `Record<number, string>`.
 * More efficient than calling useDisplayName N times.
 */
export function useDisplayNames(userIds: number[]): Record<number, string> {
  const currentUserId = Number(useAuthStore((s) => s.user?.id) ?? 0);
  const nameMap = useNameMap(currentUserId);

  return useMemo(() => {
    const result: Record<number, string> = {};
    userIds.forEach((id) => {
      if (id === currentUserId) {
        result[id] = "You";
      } else {
        result[id] = nameMap[id] ?? `User ${id}`;
      }
    });
    return result;
  }, [userIds, currentUserId, nameMap]);
}
