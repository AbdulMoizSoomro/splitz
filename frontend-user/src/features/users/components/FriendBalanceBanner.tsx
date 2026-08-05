import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { DollarSign, TrendingUp, TrendingDown, Globe } from "lucide-react";
import type { User } from "../../types/user";
import type { Group } from "../../types/group";

interface FriendBalanceBannerProps {
  friend: User;
  netBalance: number;
  directBalance: number;
  sharedGroups: Group[];
  groupBalancesMap: Record<number, number>;
  onOpenSettlementModal: () => void;
}

export function FriendBalanceBanner({
  friend,
  netBalance,
  directBalance,
  sharedGroups,
  groupBalancesMap,
  onOpenSettlementModal,
}: FriendBalanceBannerProps) {
  return (
    <div className="space-y-6">
      <Card className="overflow-hidden">
        <CardContent
          className={`p-4 ${
            netBalance > 0
              ? "bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400"
              : netBalance < 0
                ? "bg-orange-50 dark:bg-orange-900/20 text-orange-700 dark:text-orange-400"
                : "bg-muted text-muted-foreground"
          }`}
        >
          <p className="text-sm font-medium uppercase tracking-wider mb-2">
            Net Balance
          </p>
          <div className="flex items-center justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                {netBalance > 0 ? (
                  <TrendingUp size={24} />
                ) : netBalance < 0 ? (
                  <TrendingDown size={24} />
                ) : (
                  <DollarSign size={24} />
                )}
                <span className="text-3xl font-bold">
                  {netBalance === 0 ? "" : netBalance > 0 ? "+" : ""}
                  {netBalance.toFixed(2)}
                </span>
              </div>
              <p className="text-xs mt-2 opacity-80">
                {netBalance > 0
                  ? `${friend.firstName} owes you`
                  : netBalance < 0
                    ? `You owe ${friend.firstName}`
                    : "You are all settled up!"}
              </p>
            </div>
            <Button
              variant={netBalance === 0 ? "outline" : "default"}
              className="flex items-center gap-2 shrink-0 bg-background text-foreground hover:bg-muted"
              onClick={onOpenSettlementModal}
            >
              <DollarSign size={18} />
              Settle Debt
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-lg font-bold text-foreground">
            Balance Breakdown
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {/* Direct Personal Balance Row */}
          <div className="flex items-center justify-between p-3 bg-indigo-50/40 border border-indigo-100/60 rounded-xl transition-all hover:bg-indigo-50/80">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-indigo-100 flex items-center justify-center text-indigo-600 shadow-sm shrink-0">
                <Globe size={18} />
              </div>
              <div>
                <span className="font-semibold text-foreground block text-xs tracking-tight">
                  Direct Balance
                </span>
                <span className="text-[10px] text-muted-foreground font-medium">
                  Personal Settlements
                </span>
              </div>
            </div>
            <div className="text-right">
              <span
                className={`text-sm font-bold tracking-tight ${
                  directBalance > 0
                    ? "text-emerald-600"
                    : directBalance < 0
                      ? "text-rose-600"
                      : "text-gray-500"
                }`}
              >
                {directBalance === 0
                  ? "$0.00"
                  : directBalance > 0
                    ? `+$${directBalance.toFixed(2)}`
                    : `-$${Math.abs(directBalance).toFixed(2)}`}
              </span>
            </div>
          </div>

          {/* Group Balances List */}
          {sharedGroups.length > 0 && (
            <div className="pt-2 border-t border-border space-y-2">
              <p className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider px-1">
                Group Splits
              </p>
              {sharedGroups.map((group) => {
                const bal = groupBalancesMap[group.id] || 0;
                return (
                  <div
                    key={group.id}
                    className="flex items-center justify-between p-2.5 hover:bg-muted/80 rounded-lg transition-colors"
                  >
                    <span
                      className="text-xs font-semibold text-foreground truncate max-w-[130px]"
                      title={group.name}
                    >
                      {group.name}
                    </span>
                    <span
                      className={`text-xs font-bold ${
                        bal > 0
                          ? "text-emerald-600 dark:text-emerald-400"
                          : bal < 0
                            ? "text-rose-600 dark:text-rose-400"
                            : "text-muted-foreground"
                      }`}
                    >
                      {bal === 0
                        ? "$0.00"
                        : bal > 0
                          ? `+$${bal.toFixed(2)}`
                          : `-$${Math.abs(bal).toFixed(2)}`}
                    </span>
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
