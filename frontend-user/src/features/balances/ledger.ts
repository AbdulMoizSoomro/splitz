import type { Balance, Debt } from "../../types/group";
import type { GroupBalance } from "../../types/user";

/**
 * The interpersonal ledger — one module owns the money position math that used
 * to be re-derived in five call sites, each with its own shape and sign
 * convention.
 *
 * The module is "deep": a small pure surface (`deriveLedger`, `deriveCounterparties`,
 * `decomposePosition`) hides the group fan-out and the sign conventions:
 *
 * - a **counterparty** is one other person the ledger key has a non-zero
 *   position with, aggregated across every shared group;
 * - `balance > 0` always means *"they owe the key"*; `balance < 0` means
 *   *"the key owes them"* (same sign as the backend's group `Balance.balance`);
 * - `decomposePosition` implements the `direct = total − groupTotal` rule once;
 * - names are resolved from the per-group member rows, falling back to the
 *   simplified-debt usernames — so name resolution and money positions share
 *   one data source.
 */

/** The smallest float magnitude still treated as "real money". */
export const MONEY_TOLERANCE = 0.01;

/** A per-group balance slice, as returned by `groupService.getBalances`. */
export interface GroupBalanceSlice {
  groupId: number;
  groupName?: string;
  balances?: Balance[];
  simplifiedDebts?: Debt[];
}

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
  /** userId → display name for every person seen in the ledger's groups. */
  memberNames: Record<number, string>;
  /** The key's balance inside each of its groups. */
  groupPositions: GroupPosition[];
  /** The overall position (group + direct decomposition). */
  position: LedgerPosition;
}

function fullName(
  b: { firstName?: string; lastName?: string; username?: string },
): string {
  return `${b.firstName ?? ""} ${b.lastName ?? ""}`.trim() || b.username || "";
}

/** userId → name for everyone appearing in the slices (member rows first). */
export function memberNames(slices: GroupBalanceSlice[]): Record<number, string> {
  const map: Record<number, string> = {};

  for (const slice of slices) {
    for (const balance of slice.balances ?? []) {
      const name = fullName(balance);
      if (name && map[balance.userId] === undefined) {
        map[balance.userId] = name;
      }
    }
    for (const debt of slice.simplifiedDebts ?? []) {
      if (debt.fromUsername && map[debt.from] === undefined) {
        map[debt.from] = debt.fromUsername;
      }
      if (debt.toUsername && map[debt.to] === undefined) {
        map[debt.to] = debt.toUsername;
      }
    }
  }

  return map;
}

/**
 * Derive the per-counterparty positions from the simplified-debt edges.
 *
 * A debt `from → to, amount` means "`from` owes `to`", so from the key's
 * point of view it is a negative position (the key owes) when the key is
 * `from`, and a positive position (they owe the key) when the key is `to`.
 * Positions for the same counterparty are aggregated across all groups.
 */
export function deriveCounterparties(
  key: number,
  slices: GroupBalanceSlice[],
): Counterparty[] {
  const names = memberNames(slices);
  const byId = new Map<number, Counterparty>();

  for (const slice of slices) {
    const groupName = slice.groupName ?? `Group #${slice.groupId}`;
    for (const debt of slice.simplifiedDebts ?? []) {
      let otherId: number;
      let delta: number;

      if (debt.from === key && debt.to !== key) {
        otherId = debt.to;
        delta = -debt.amount;
      } else if (debt.to === key && debt.from !== key) {
        otherId = debt.from;
        delta = debt.amount;
      } else {
        continue;
      }

      let counterparty = byId.get(otherId);
      if (!counterparty) {
        counterparty = { userId: otherId, name: "", balance: 0, groups: [] };
        byId.set(otherId, counterparty);
      }

      counterparty.balance += delta;
      if (!counterparty.groups.some((g) => g.id === slice.groupId)) {
        counterparty.groups.push({ id: slice.groupId, name: groupName });
      }
    }
  }

  return [...byId.values()].map((cp) => ({
    ...cp,
    name: names[cp.userId] ?? "",
  }));
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

/** Derive the complete ledger from a net-balance source and group slices. */
export function deriveLedger(
  key: number,
  source: PositionSource | undefined,
  slices: GroupBalanceSlice[],
): Ledger {
  const { position, groupPositions } = decomposePosition(source ?? { total: 0 });

  return {
    key,
    counterparties: deriveCounterparties(key, slices),
    memberNames: memberNames(slices),
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
