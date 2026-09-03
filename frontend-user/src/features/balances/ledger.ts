import type { GroupBalance } from "../../types/user";

/**
 * The interpersonal ledger — one module owns the money position math that used
 * to be re-derived in five call sites, each with its own shape and sign
 * convention.
 *
 * The module is "deep": a small pure surface (`deriveLedger`, `decomposePosition`)
 * hides the group decomposition and the sign conventions:
 *
 * - a **counterparty** is one other person the ledger key has a non-zero
 *   position with, aggregated across every shared group;
 * - `balance > 0` always means *"they owe the key"*; `balance < 0` means
 *   *"the key owes them"* (same sign as the backend's group `Balance.balance`);
 * - `decomposePosition` implements the `direct = total − groupTotal` rule once;
 */

/** The smallest float magnitude still treated as "real money". */
export const MONEY_TOLERANCE = 0.01;

/** A group identified by id and name (the shape counterparties group by). */
export interface GroupRef {
  id: number;
  name: string;
}

/** One "key ↔ someone else" money line, aggregated across shared groups. */
export interface Counterparty {
  userId: number;
  /** Best-known display name ("First Last", else username, else ""). */
  name: string;
  /** Positive → they owe `key`; negative → `key` owes them. */
  balance: number;
  /** The shared groups where this position exists. */
  groups: GroupRef[];
  username?: string;
  firstName?: string;
  lastName?: string;
  email?: string;
}

/** The key's balance inside one group. */
export interface GroupPosition {
  groupId: number;
  groupName: string;
  /** Positive → the key is owed inside this group. */
  balance: number;
}

/** The key's overall position, decomposed into group and direct parts. */
export interface LedgerPosition {
  /** Global net (includes direct settlements). Positive → key is owed. */
  total: number;
  /** Sum of the group positions. */
  groupTotal: number;
  /** total − groupTotal: the non-group ("direct personal") part. */
  direct: number;
}

/** A net-balance source the decomposition rule can consume. */
export interface PositionSource {
  total: number;
  balances?: Array<{ groupId: number; groupName?: string; balance: number }>;
}

/** The full ledger: per-counterparty positions plus the overall position. */
export interface Ledger {
  key: number;
  /** Everyone the key has a non-zero position with, one entry per person. */
  counterparties: Counterparty[];
  /** userId → display name for every person seen in the ledger's counterparties. */
  memberNames: Record<number, string>;
  /** The key's balance inside each of its groups. */
  groupPositions: GroupPosition[];
  /** The overall position (group + direct decomposition). */
  position: LedgerPosition;
}

/**
 * The one decomposition rule: `direct = total − groupTotal`.
 * `source.total` may be a global user balance or a friend's net balance —
 * both use the same sign convention (positive = the key is owed).
 */
export function decomposePosition(
  source: PositionSource,
): { position: LedgerPosition; groupPositions: GroupPosition[] } {
  const groupPositions: GroupPosition[] = (source.balances ?? []).map((gb) => ({
    groupId: gb.groupId,
    groupName: gb.groupName ?? "",
    balance: gb.balance,
  }));
  const groupTotal = groupPositions.reduce((sum, p) => sum + p.balance, 0);

  return {
    position: {
      total: source.total,
      groupTotal,
      direct: source.total - groupTotal,
    },
    groupPositions,
  };
}

/** The key's net balance inside one group's balance sheet (0 when absent). */
export function balanceInGroup(
  balances: Array<{ userId: number; balance: number }>,
  userId: number,
): number {
  return balances.find((b) => b.userId === userId)?.balance ?? 0;
}

/** Derive the complete ledger from a net-balance source and counterparties. */
export function deriveLedger(
  key: number,
  source: PositionSource | undefined,
  counterparties: Counterparty[] = [],
): Ledger {
  const { position, groupPositions } = decomposePosition(source ?? { total: 0 });

  const names: Record<number, string> = {};
  for (const cp of counterparties) {
    const displayName =
      cp.name ||
      [cp.firstName, cp.lastName].filter(Boolean).join(" ").trim() ||
      cp.username ||
      "";
    if (displayName) {
      names[cp.userId] = displayName;
    }
  }

  return {
    key,
    counterparties,
    memberNames: names,
    groupPositions,
    position,
  };
}

/** Convenience: the user-level net-balance source for the ledger. */
export function userPositionSource(
  userBalances: {
    totalBalance: number;
    groupBalances?: GroupBalance[];
  } | undefined,
): PositionSource | undefined {
  if (!userBalances) return undefined;
  return {
    total: userBalances.totalBalance,
    balances: userBalances.groupBalances,
  };
}
