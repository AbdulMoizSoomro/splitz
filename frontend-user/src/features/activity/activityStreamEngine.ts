import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { activityService } from "./activityService";
import { groupService } from "../groups/groupService";
import { useAuthStore } from "../../store/authStore";
import { useDisplayNames } from "../../hooks/useDisplayName";
import type { Expense, ExpenseSplit } from "../../types/expense";
import type { Settlement } from "../balances/settlementService";

// ---------------------------------------------------------------------------
// Pure activity-stream domain.
//
// The merge of global expenses and settlements into one typed feed, the filter
// predicate, and the day-bucket split all live here as pure functions, so a
// view (or a test) never needs to parse ISO strings or sort a mixed feed itself.
// ---------------------------------------------------------------------------

export type ActivityKind = "EXPENSE" | "SETTLEMENT";
export type ActivityFilter = "ALL" | ActivityKind;

/** The fixed filter vocabulary, shared by the engine and any view. */
export const ACTIVITY_FILTERS: readonly ActivityFilter[] = [
  "ALL",
  "EXPENSE",
  "SETTLEMENT",
] as const;

const FILTER_LABELS: Record<ActivityFilter, string> = {
  ALL: "All Activity",
  EXPENSE: "Expenses",
  SETTLEMENT: "Settlements & Payments",
};

/** Human label for a filter pill. */
export function activityFilterLabel(filter: ActivityFilter): string {
  return FILTER_LABELS[filter];
}

interface ExpenseEntry {
  id: number;
  type: "EXPENSE";
  date: string;
  description: string;
  amount: number;
  paidBy: number;
  groupId: number;
  splits: ExpenseSplit[];
}

interface SettlementEntry {
  id: number;
  type: "SETTLEMENT";
  date: string;
  amount: number;
  payerId: number;
  payeeId: number;
  groupId?: number;
  status: Settlement["status"];
}

export type ActivityEntry = ExpenseEntry | SettlementEntry;

export interface ActivityNameResolver {
  nameOf: (userId: number) => string;
  groupNameOf: (groupId: number) => string;
}

export type ActivityCardVariant =
  | "expense-payer"
  | "expense-owe"
  | "expense-uninvolved"
  | "settlement-payer"
  | "settlement-payee"
  | "settlement-other";

export type ActivityAmountTone = "positive" | "negative" | "neutral";

export interface ActivityCardView {
  key: string;
  type: ActivityKind;
  variant: ActivityCardVariant;
  title: string;
  subdetail: string;
  amountLabel: string;
  amountTone: ActivityAmountTone;
  /** "Settlement Record" for settlements; formatted total for expenses. */
  footerLabel: string;
  dateLabel: string;
  timeLabel: string;
  group?: { id: number; name: string };
  isDirect?: boolean;
  status?: Settlement["status"];
  statusLabel?: string;
}

export interface ActivityBucket {
  label: string;
  items: ActivityEntry[];
}

export interface GroupedActivity {
  label: string;
  items: ActivityCardView[];
}

// --- Feed construction -----------------------------------------------------

function expenseDate(e: Expense): string {
  return e.expenseDate || e.createdAt || "";
}

function settlementDate(s: Settlement): string {
  return s.settledAt || s.confirmedAt || s.paidAt || s.createdAt || "";
}

/**
 * Merges global expenses and settlements into one typed, newest-first feed.
 * Either side may be absent.
 */
export function combineFeed(
  expenses: Expense[] | undefined,
  settlements: Settlement[] | undefined,
): ActivityEntry[] {
  const items: ActivityEntry[] = [];

  (expenses ?? []).forEach((e) => {
    items.push({
      id: e.id,
      type: "EXPENSE",
      date: expenseDate(e),
      description: e.description,
      amount: e.amount,
      paidBy: e.paidBy,
      groupId: e.groupId,
      splits: e.splits,
    });
  });

  (settlements ?? []).forEach((s) => {
    items.push({
      id: s.id,
      type: "SETTLEMENT",
      date: settlementDate(s),
      amount: s.amount,
      payerId: s.payerId,
      payeeId: s.payeeId,
      groupId:
        // Direct settlements carry `groupId: 0`; treat it as "no group".
        s.groupId && s.groupId > 0 ? s.groupId : undefined,
      status: s.status,
    });
  });

  return items.sort((a, b) => {
    const ta = new Date(a.date).getTime();
    const tb = new Date(b.date).getTime();
    // Unparseable dates sort last, preserving input order between themselves.
    if (Number.isNaN(ta) && Number.isNaN(tb)) return 0;
    if (Number.isNaN(ta)) return 1;
    if (Number.isNaN(tb)) return -1;
    return tb - ta;
  });
}

// --- Filter predicate ------------------------------------------------------

/** Keeps only the feed entries matching the selected type filter. */
export function applyFilter(
  feed: ActivityEntry[],
  filter: ActivityFilter,
): ActivityEntry[] {
  if (filter === "ALL") return feed;
  return feed.filter((entry) => entry.type === filter);
}

// --- Date bucketing --------------------------------------------------------

const MONTHS = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];

function sameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

function labelFor(parsed: Date): string {
  return `${MONTHS[parsed.getMonth()]} ${parsed.getDate()}, ${parsed.getFullYear()}`;
}

/**
 * Bucket label for an ISO date relative to `now` — "Today", "Yesterday", or a
 * calendar label ("Jan 3, 2026"). Unknown/empty dates land in "Unknown".
 */
export function bucketLabelFor(dateStr: string, now: Date): string {
  if (!dateStr) return "Unknown";
  const parsed = new Date(dateStr);
  if (Number.isNaN(parsed.getTime())) return "Unknown";

  if (sameDay(parsed, now)) return "Today";

  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (sameDay(parsed, yesterday)) return "Yesterday";

  return labelFor(parsed);
}

/**
 * Groups a feed into day buckets preserving feed order. The input is assumed
 * sorted newest-first; each bucket keeps its items in that order.
 */
export function bucketize(
  entries: ActivityEntry[],
  now: Date,
): ActivityBucket[] {
  const buckets: ActivityBucket[] = [];
  for (const entry of entries) {
    const l = bucketLabelFor(entry.date, now);
    const last = buckets[buckets.length - 1];
    if (last && last.label === l) {
      last.items.push(entry);
    } else {
      buckets.push({ label: l, items: [entry] });
    }
  }
  return buckets;
}

// --- Card building ---------------------------------------------------------

export function formatDateTime(
  dateStr: string,
): { dateLabel: string; timeLabel: string } {
  if (!dateStr) return { dateLabel: "Unknown Date", timeLabel: "" };
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime()))
    return { dateLabel: "Unknown Date", timeLabel: "" };
  return {
    dateLabel: d.toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    }),
    timeLabel: d.toLocaleTimeString("en-US", {
      hour: "2-digit",
      minute: "2-digit",
    }),
  };
}

/**
 * Build the full display model for one feed entry against the current user:
 * titles, money strings, tones, and group/navigation metadata. Pure, so the
 * whole match/title/grammar is unit-tested without a view.
 */
export function buildCardView(
  entry: ActivityEntry,
  currentUserId: number,
  resolver: ActivityNameResolver,
): ActivityCardView {
  const { dateLabel, timeLabel } = formatDateTime(entry.date);

  if (entry.type === "EXPENSE") {
    const isPayer = entry.paidBy === currentUserId;
    const mySplit = entry.splits.find((s) => s.userId === currentUserId);
    const myShare = mySplit ? mySplit.shareAmount : 0;

    let title: string;
    let subdetail: string;
    let amountLabel: string;
    let amountTone: ActivityAmountTone;

    if (isPayer) {
      const lent = entry.amount - myShare;
      title = `You paid for "${entry.description}"`;
      subdetail = `You paid $${entry.amount.toFixed(2)} (you lent $${lent.toFixed(2)})`;
      amountLabel = `+ $${lent.toFixed(2)}`;
      amountTone = "positive";
    } else if (mySplit) {
      const payerName = resolver.nameOf(entry.paidBy);
      title = `${payerName} paid for "${entry.description}"`;
      subdetail = `You owe $${myShare.toFixed(2)} to ${payerName}`;
      amountLabel = `- $${myShare.toFixed(2)}`;
      amountTone = "negative";
    } else {
      title = `${resolver.nameOf(entry.paidBy)} paid for "${entry.description}"`;
      subdetail = "You are not involved in this expense";
      amountLabel = "$0.00";
      amountTone = "neutral";
    }

    return {
      key: `expense-${entry.id}`,
      type: "EXPENSE",
      variant: isPayer
        ? "expense-payer"
        : mySplit
          ? "expense-owe"
          : "expense-uninvolved",
      title,
      subdetail,
      amountLabel,
      amountTone,
      footerLabel: `Total Expense: $${entry.amount.toFixed(2)}`,
      dateLabel,
      timeLabel,
      group: {
        id: entry.groupId,
        name: resolver.groupNameOf(entry.groupId),
      },
    };
  }

  // settlement
  const isPayer = entry.payerId === currentUserId;
  const isPayee = entry.payeeId === currentUserId;
  const payerName = resolver.nameOf(entry.payerId);
  const payeeName = resolver.nameOf(entry.payeeId);

  let title: string;
  let subdetail: string;
  let amountLabel: string;
  let amountTone: ActivityAmountTone;

  if (isPayer) {
    title = `You sent a payment to ${payeeName}`;
    subdetail = `You paid $${entry.amount.toFixed(2)} to ${payeeName}`;
    amountLabel = `+ $${entry.amount.toFixed(2)}`;
    amountTone = "positive";
  } else if (isPayee) {
    title = `${payerName} sent you a payment`;
    subdetail = `You received $${entry.amount.toFixed(2)} from ${payerName}`;
    amountLabel = `-$${entry.amount.toFixed(2)}`;
    amountTone = "positive";
  } else {
    title = `${payerName} paid ${payeeName}`;
    subdetail = `Settled $${entry.amount.toFixed(2)}`;
    amountLabel = `$${entry.amount.toFixed(2)}`;
    amountTone = "neutral";
  }

  const card: ActivityCardView = {
    key: `settlement-${entry.id}`,
    type: "SETTLEMENT",
    variant: isPayer
      ? "settlement-payer"
      : isPayee
        ? "settlement-payee"
        : "settlement-other",
    title,
    subdetail,
    amountLabel,
    amountTone,
    footerLabel: "Settlement Record",
    dateLabel,
    timeLabel,
    status: entry.status,
    statusLabel: entry.status.replace("_", " "),
  };

  if (entry.groupId) {
    card.group = {
      id: entry.groupId,
      name: resolver.groupNameOf(entry.groupId),
    };
  } else {
    card.isDirect = true;
  }

  return card;
}

// ---------------------------------------------------------------------------
// Hook — owns the queries and name resolution; exposes the grouped feed.
// ---------------------------------------------------------------------------

export function useActivityStreamEngine() {
  const { user } = useAuthStore();
  const currentUserId = Number(user?.id);
  const [filter, setFilter] = useState<ActivityFilter>("ALL");

  const activityQuery = useQuery({
    queryKey: ["global-activity"],
    queryFn: () => activityService.getGlobalActivity(),
  });

  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: () => groupService.getGroups(),
  });

  const uniqueUserIds = useMemo(() => {
    if (!activityQuery.data) return [];
    const ids = new Set<number>();
    activityQuery.data.expenses.forEach((e) => ids.add(e.paidBy));
    activityQuery.data.settlements.forEach((s) => {
      ids.add(s.payerId);
      ids.add(s.payeeId);
    });
    return [...ids];
  }, [activityQuery.data]);

  const nameMap = useDisplayNames(uniqueUserIds);

  const resolver: ActivityNameResolver = useMemo(
    () => ({
      nameOf: (userId) => nameMap[userId] ?? `User ${userId}`,
      groupNameOf: (groupId) =>
        groupsQuery.data?.find((g) => g.id === groupId)?.name ??
        `Group #${groupId}`,
    }),
    [nameMap, groupsQuery.data],
  );

  const groupedActivity = useMemo(() => {
    const feed = applyFilter(
      combineFeed(
        activityQuery.data?.expenses,
        activityQuery.data?.settlements,
      ),
      filter,
    );
    return bucketize(feed, new Date()).map((bucket): GroupedActivity => ({
      label: bucket.label,
      items: bucket.items.map((e) => buildCardView(e, currentUserId, resolver)),
    }));
  }, [activityQuery.data, filter, currentUserId, resolver]);

  return {
    groupedActivity,
    filter,
    setFilter,
    isLoading: activityQuery.isLoading,
  };
}

export type ActivityStreamEngine = ReturnType<typeof useActivityStreamEngine>;