import { Card, CardContent } from "@/components/ui/card";
import {
  ArrowRightLeft,
  Calendar,
  Clock,
  ArrowUpRight,
  ArrowDownLeft,
  Info,
  Globe,
} from "lucide-react";
import type { ActivityCardView } from "../activityStreamEngine";

type Visual = {
  icon: typeof ArrowUpRight;
  badgeBg: string;
  amountColor: string;
};

const VISUAL: Record<ActivityCardView["variant"], Visual> = {
  "expense-payer": {
    icon: ArrowUpRight,
    badgeBg: "bg-emerald-50 text-emerald-700 border border-emerald-100",
    amountColor: "text-emerald-600 font-bold",
  },
  "expense-owe": {
    icon: ArrowDownLeft,
    badgeBg: "bg-rose-50 text-rose-700 border border-rose-100",
    amountColor: "text-rose-600 font-bold",
  },
  "expense-uninvolved": {
    icon: Info,
    badgeBg: "bg-slate-100 text-slate-500",
    amountColor: "text-slate-500 font-semibold",
  },
  "settlement-payer": {
    icon: ArrowUpRight,
    badgeBg: "bg-emerald-50 text-emerald-700 border border-emerald-100",
    amountColor: "text-emerald-600 font-bold",
  },
  "settlement-payee": {
    icon: ArrowDownLeft,
    badgeBg: "bg-teal-50 text-teal-700 border border-teal-100",
    amountColor: "text-emerald-600 font-bold",
  },
  "settlement-other": {
    icon: ArrowRightLeft,
    badgeBg: "bg-slate-100 text-slate-500",
    amountColor: "text-slate-500 font-semibold",
  },
};

const STATUS_BADGE: Record<string, string> = {
  COMPLETED:
    "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-300",
  MARKED_PAID:
    "bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300",
  PENDING: "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300",
};

export function ActivityCard({
  view,
  onGroupClick,
}: {
  view: ActivityCardView;
  onGroupClick: (groupId: number) => void;
}) {
  const visual = VISUAL[view.variant];
  const Icon = visual.icon;

  const groupPill = view.group && (
    <button
      onClick={(e) => {
        e.stopPropagation();
        onGroupClick(view.group!.id);
      }}
      className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300 hover:bg-indigo-100 transition-colors border border-indigo-100 dark:border-indigo-800"
      title="Go to group details"
    >
      {view.group.name}
    </button>
  );

  return (
    <Card className="hover:translate-y-[-2px] hover:shadow-lg transition-all duration-300 border-border">
      <CardContent className="p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        {/* Left: Info */}
        <div className="flex items-center gap-4">
          <div className={`p-3 rounded-xl ${visual.badgeBg} shadow-sm shrink-0`}>
            <Icon size={24} />
          </div>
          <div className="space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-bold text-foreground text-base">{view.title}</span>

              {groupPill}

              {view.isDirect && (
                <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-indigo-50 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-800 flex items-center gap-1 shadow-sm uppercase tracking-wide">
                  <Globe size={11} className="text-indigo-500 dark:text-indigo-400" /> Direct
                </span>
              )}

              {view.status && (
                <span
                  className={`px-2 py-0.5 text-[10px] font-bold rounded uppercase ${
                    STATUS_BADGE[view.status] ?? STATUS_BADGE.PENDING
                  }`}
                >
                  {view.statusLabel}
                </span>
              )}
            </div>

            <p className="text-sm font-medium text-muted-foreground">{view.subdetail}</p>

            {/* Date and Time */}
            <div className="flex items-center gap-3 text-xs text-muted-foreground font-medium pt-1">
              <span className="flex items-center gap-1">
                <Calendar size={12} /> {view.dateLabel}
              </span>
              <span className="flex items-center gap-1">
                <Clock size={12} /> {view.timeLabel}
              </span>
            </div>
          </div>
        </div>

        {/* Right: Net amount + footer */}
        <div className="text-left sm:text-right shrink-0">
          <div className={`text-lg ${visual.amountColor}`}>{view.amountLabel}</div>
          <div className="text-xs text-muted-foreground font-semibold mt-0.5">
            {view.footerLabel}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}