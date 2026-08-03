import { useMemo } from "react";
import { useLedger } from "./useLedger";

// ---------------------------------------------------------------------------
// Internal hook — shared by useDisplayName and useDisplayNames.
//
// Names come from two sources:
//   1. friends  → "First Last"
//   2. the ledger's member map (covers Temp Friends and group members) → "First Last"
//
// The ledger owns the group-balance fan-out, so name resolution no longer
// drags a per-hook N+1 along just to print a label.
//
// Self-detection ("You") and the numeric fallback are applied by the callers
// so this map only contains *other* users.
// ---------------------------------------------------------------------------
function useNameState(): {
  currentUserId: number;
  nameMap: Record<number, string>;
} {
  const { key, friends, ledger } = useLedger({ detail: true });

  return useMemo(() => {
    const nameMap: Record<number, string> = {};

    // Source 1: friends
    (friends ?? []).forEach((f) => {
      const fullName = `${f.firstName} ${f.lastName}`.trim();
      if (fullName) nameMap[f.id] = fullName;
    });

    // Source 2: ledger member names (only set if not already resolved)
    const memberMap = ledger?.memberNames ?? {};
    Object.entries(memberMap).forEach(([idStr, name]) => {
      const id = Number(idStr);
      if (!nameMap[id] && name) nameMap[id] = name;
    });

    return { currentUserId: key, nameMap };
  }, [key, friends, ledger]);
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Resolves a single userId to a display name.
 * Self → "You", friends/group members → "First Last", unknown → "User N".
 */
export function useDisplayName(userId: number): string {
  const { currentUserId, nameMap } = useNameState();

  if (userId === currentUserId) return "You";
  return nameMap[userId] ?? `User ${userId}`;
}

/**
 * Bulk variant: resolves a list of userIds to a `Record<number, string>`.
 * More efficient than calling useDisplayName N times.
 */
export function useDisplayNames(userIds: number[]): Record<number, string> {
  const { currentUserId, nameMap } = useNameState();

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