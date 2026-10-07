import { useQuery } from "@tanstack/react-query";
import { simplificationService } from "./simplificationService";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Sparkles, ArrowRight, TrendingDown, DollarSign, UserX, Loader2 } from "lucide-react";

interface DebtSimplificationPlanCardProps {
  groupId: number;
  currentUserId: number;
  onSettleDebt?: (debt: { from: number; to: number; amount: number; toUsername: string }) => void;
}

export const DebtSimplificationPlanCard = ({
  groupId,
  currentUserId,
  onSettleDebt,
}: DebtSimplificationPlanCardProps) => {
  const { data: plan, isLoading, error } = useQuery({
    queryKey: ["group-simplification-plan", groupId],
    queryFn: () => simplificationService.getSimplificationPlan(groupId),
  });

  if (isLoading) {
    return (
      <Card>
        <CardContent className="flex justify-center p-8" data-testid="plan-loader">
          <Loader2 className="animate-spin text-blue-600" size={24} />
        </CardContent>
      </Card>
    );
  }

  if (error || !plan) {
    return null;
  }

  if (!plan.simplificationEnabled) {
    return (
      <Card className="border-dashed border-border bg-muted/30">
        <CardContent className="py-6 text-center">
          <p className="text-sm text-muted-foreground italic">
            Debt simplification is disabled for this group by governance.
          </p>
        </CardContent>
      </Card>
    );
  }

  const origCount = plan.originalTransactionCount ?? 0;
  const simpCount = plan.simplifiedTransactionCount ?? 0;
  const savedCount = Math.max(0, origCount - simpCount);
  const reductionPercent = origCount > 0 ? Math.round((savedCount / origCount) * 100) : 0;

  return (
    <Card className="border-blue-500/20 bg-card shadow-sm" data-testid="simplification-plan-card">
      <CardHeader className="pb-3">
        <CardTitle className="text-foreground flex items-center justify-between text-lg">
          <div className="flex items-center gap-2">
            <Sparkles size={20} className="text-blue-500 animate-pulse" />
            <span>Suggested Settlement Plan</span>
          </div>
          <div className="flex items-center gap-2">
            <Badge className="bg-blue-500/10 text-blue-600 border-blue-500/20">
              {plan.scope === "CROSS_GROUP" ? "Global Cross-Group Netting" : "Intra-Group Netting"}
            </Badge>
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Metrics Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 p-3 bg-muted/50 rounded-xl border border-border">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-blue-500/10 text-blue-600 shrink-0">
              <TrendingDown size={18} />
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Transactions</p>
              <p className="text-sm font-bold text-foreground">
                {simpCount} <span className="text-xs font-normal text-muted-foreground">from {origCount}</span>
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-600 shrink-0">
              <Sparkles size={18} />
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Reduction Saved</p>
              <p className="text-sm font-bold text-emerald-600">
                {reductionPercent}% <span className="text-xs font-normal">({savedCount} fewer)</span>
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-purple-500/10 text-purple-600 shrink-0">
              <DollarSign size={18} />
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Total Debt Volume</p>
              <p className="text-sm font-bold text-foreground">
                ${(plan.totalDebtVolume ?? 0).toFixed(2)}
              </p>
            </div>
          </div>
        </div>

        {/* Opt-Out Users Callout */}
        {plan.optedOutUserIds && plan.optedOutUserIds.length > 0 && (
          <div className="flex items-center gap-2 p-2.5 bg-amber-500/10 border border-amber-500/20 rounded-lg text-amber-700 dark:text-amber-400 text-xs">
            <UserX size={16} className="shrink-0 text-amber-600" />
            <span>
              {plan.optedOutUserIds.length} member(s) opted out of debt netting. Their direct balances are preserved.
            </span>
          </div>
        )}

        {/* Suggested Transactions List */}
        {!plan.transactions || plan.transactions.length === 0 ? (
          <p className="text-muted-foreground text-sm italic text-center py-4">
            No simplified payments needed — all balances are settled!
          </p>
        ) : (
          <ScrollArea className="h-[220px] pr-2">
            <div className="space-y-3">
              {plan.transactions.map((tx, idx) => {
                const isPayer = tx.fromUserId === currentUserId;
                const isPayee = tx.toUserId === currentUserId;

                return (
                  <div
                    key={idx}
                    className={`flex items-center justify-between p-3 rounded-lg border transition-colors ${
                      isPayer
                        ? "bg-destructive/5 border-destructive/20"
                        : isPayee
                        ? "bg-emerald-500/5 border-emerald-500/20"
                        : "bg-muted/40 border-border"
                    }`}
                  >
                    <div className="flex items-center gap-3 flex-1 min-w-0 pr-2">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-1.5 text-sm font-medium truncate">
                          <span className={isPayer ? "text-destructive font-semibold" : "text-foreground"}>
                            {isPayer ? "You" : tx.fromUsername || `User ${tx.fromUserId}`}
                          </span>
                          <ArrowRight size={14} className="text-muted-foreground shrink-0" />
                          <span className={isPayee ? "text-emerald-600 font-semibold" : "text-foreground"}>
                            {isPayee ? "You" : tx.toUsername || `User ${tx.toUserId}`}
                          </span>
                        </div>
                        <Badge variant="outline" className="text-[10px] py-0 mt-0.5 text-muted-foreground">
                          {tx.status || "SUGGESTED"}
                        </Badge>
                      </div>
                    </div>

                    <div className="flex items-center gap-3 shrink-0">
                      <span className={`text-base font-bold ${isPayer ? "text-destructive" : isPayee ? "text-emerald-600" : "text-foreground"}`}>
                        ${tx.amount.toFixed(2)}
                      </span>

                      {isPayer && onSettleDebt && (
                        <Button
                          size="sm"
                          className="bg-blue-600 hover:bg-blue-700 text-white"
                          onClick={() =>
                            onSettleDebt({
                              from: tx.fromUserId,
                              to: tx.toUserId,
                              amount: tx.amount,
                              toUsername: tx.toUsername || `User ${tx.toUserId}`,
                            })
                          }
                          aria-label={`Settle ${tx.amount} to ${tx.toUsername}`}
                        >
                          Settle
                        </Button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </ScrollArea>
        )}
      </CardContent>
    </Card>
  );
};
