import React from "react";
import { ArrowDownLeft, ArrowUpRight, Scale } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import type { FriendsSummary } from "../friendsLedger";

interface FriendsSummaryBannerProps {
  summary: FriendsSummary;
}

export const FriendsSummaryBanner: React.FC<FriendsSummaryBannerProps> = ({
  summary,
}) => {
  const { totalOwed, totalOwe, netBalance } = summary;

  const formattedNet =
    netBalance > 0.009
      ? `+$${netBalance.toFixed(2)}`
      : netBalance < -0.009
        ? `-$${Math.abs(netBalance).toFixed(2)}`
        : "$0.00";

  const netColor =
    netBalance > 0.009
      ? "text-emerald-600 dark:text-emerald-400"
      : netBalance < -0.009
        ? "text-rose-600 dark:text-rose-400"
        : "text-muted-foreground";

  return (
    <div className="grid grid-cols-3 gap-2 sm:gap-4">
      {/* You are owed */}
      <Card className="border-emerald-200/60 dark:border-emerald-900/40 bg-gradient-to-br from-emerald-50/50 to-background dark:from-emerald-950/20 dark:to-background shadow-xs">
        <CardContent className="p-2.5 sm:p-4 flex items-center justify-between">
          <div className="min-w-0 flex-1">
            <p className="text-[11px] sm:text-xs font-medium text-emerald-800 dark:text-emerald-300 uppercase tracking-wider truncate">
              You are owed
            </p>
            <p className="text-sm sm:text-2xl font-bold text-emerald-600 dark:text-emerald-400 mt-0.5 sm:mt-1 truncate">
              ${totalOwed.toFixed(2)}
            </p>
          </div>
          <div className="hidden sm:flex w-10 h-10 rounded-full bg-emerald-100 dark:bg-emerald-900/50 items-center justify-center text-emerald-600 dark:text-emerald-400 shrink-0 ml-2">
            <ArrowDownLeft size={20} />
          </div>
        </CardContent>
      </Card>

      {/* You owe */}
      <Card className="border-rose-200/60 dark:border-rose-900/40 bg-gradient-to-br from-rose-50/50 to-background dark:from-rose-950/20 dark:to-background shadow-xs">
        <CardContent className="p-2.5 sm:p-4 flex items-center justify-between">
          <div className="min-w-0 flex-1">
            <p className="text-[11px] sm:text-xs font-medium text-rose-800 dark:text-rose-300 uppercase tracking-wider truncate">
              You owe
            </p>
            <p className="text-sm sm:text-2xl font-bold text-rose-600 dark:text-rose-400 mt-0.5 sm:mt-1 truncate">
              ${totalOwe.toFixed(2)}
            </p>
          </div>
          <div className="hidden sm:flex w-10 h-10 rounded-full bg-rose-100 dark:bg-rose-900/50 items-center justify-center text-rose-600 dark:text-rose-400 shrink-0 ml-2">
            <ArrowUpRight size={20} />
          </div>
        </CardContent>
      </Card>

      {/* Net balance */}
      <Card className="border-border bg-card shadow-xs">
        <CardContent className="p-2.5 sm:p-4 flex items-center justify-between">
          <div className="min-w-0 flex-1">
            <p className="text-[11px] sm:text-xs font-medium text-muted-foreground uppercase tracking-wider truncate">
              Net balance
            </p>
            <p className={`text-sm sm:text-2xl font-bold mt-0.5 sm:mt-1 truncate ${netColor}`}>
              {formattedNet}
            </p>
          </div>
          <div className="hidden sm:flex w-10 h-10 rounded-full bg-muted items-center justify-center text-muted-foreground shrink-0 ml-2">
            <Scale size={20} />
          </div>
        </CardContent>
      </Card>
    </div>
  );
};
