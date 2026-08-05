import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { DollarSign } from "lucide-react";
import type { TabType } from "../useGroupDetailsSession";

interface GroupBalanceBannerProps {
  isBalancesLoading: boolean;
  currentUserBalance: number;
  onViewDetails: (tab: TabType) => void;
}

export function GroupBalanceBanner({
  isBalancesLoading,
  currentUserBalance,
  onViewDetails,
}: GroupBalanceBannerProps) {
  if (isBalancesLoading || currentUserBalance === 0) {
    return null;
  }

  return (
    <Card className="shrink-0 my-4 shadow-sm border-border">
      <CardContent className="p-4 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <div
            className={`p-3 rounded-full flex items-center justify-center ${
              currentUserBalance > 0
                ? "bg-emerald-500/10 text-emerald-500"
                : "bg-destructive/10 text-destructive"
            }`}
          >
            <DollarSign size={24} />
          </div>
          <div>
            <p className="text-sm font-medium text-muted-foreground">
              Your Group Balance
            </p>
            <h3
              className={`text-xl font-bold ${
                currentUserBalance > 0
                  ? "text-emerald-500"
                  : "text-destructive"
              }`}
            >
              {currentUserBalance > 0 ? "You are owed" : "You owe"} $
              {Math.abs(currentUserBalance).toFixed(2)}
            </h3>
          </div>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onViewDetails("balances")}
          className={
            currentUserBalance > 0
              ? "text-emerald-500 border-emerald-500/30 hover:bg-emerald-500/10 hover:text-emerald-600"
              : "text-destructive border-destructive/30 hover:bg-destructive/10 hover:text-destructive"
          }
        >
          View Details
        </Button>
      </CardContent>
    </Card>
  );
}
