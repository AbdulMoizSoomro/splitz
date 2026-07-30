import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { settlementService } from "./settlementService";
import { groupService } from "../groups/groupService";
import { useAuthStore } from "../../store/authStore";
import { toast } from "sonner";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Loader2, ArrowRight, Clock } from "lucide-react";
import { useState } from "react";
import { ScrollArea } from "@/components/ui/scroll-area";
import { DebtSimplificationPlanCard } from "./DebtSimplificationPlanCard";
import { SimplificationSettingsCard } from "./SimplificationSettingsCard";

interface GroupBalancesProps {
  groupId: number;
}

const GroupBalances = ({ groupId }: GroupBalancesProps) => {
  const { user } = useAuthStore();
  const currentUserId = Number(user?.id);
  const queryClient = useQueryClient();

  const [isSettleModalOpen, setIsAddSettleModalOpen] = useState(false);
  const [selectedDebt, setSelectedDebt] = useState<{
    from: number;
    to: number;
    amount: number;
    toUsername: string;
  } | null>(null);

  const { data: group } = useQuery({
    queryKey: ["group", groupId],
    queryFn: () => groupService.getGroup(groupId),
  });

  const { data: balances, isLoading } = useQuery({
    queryKey: ["group-balances", groupId],
    queryFn: () => groupService.getBalances(groupId),
  });

  const { data: settlements, isLoading: isLoadingSettlements } = useQuery({
    queryKey: ["group-settlements", groupId],
    queryFn: () => settlementService.getSettlementsByGroup(groupId),
  });

  const createSettlementMutation = useMutation({
    mutationFn: async (debt: { to: number; amount: number }) => {
      const s = await settlementService.createSettlement({
        payerId: currentUserId,
        payeeId: debt.to,
        amount: debt.amount,
        currency: "USD",
        groupId,
      });
      if (s.status === "PENDING") {
        return settlementService.markAsPaid(s.id);
      }
      return s;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-balances", groupId] });
      queryClient.invalidateQueries({ queryKey: ["group-simplification-plan", groupId] });
      queryClient.invalidateQueries({
        queryKey: ["group-settlements", groupId],
      });
      toast.success("Payment recorded and marked as paid");
      setIsAddSettleModalOpen(false);
    },
    onError: () => {
      toast.error("Failed to record payment");
    },
  });

  const confirmMutation = useMutation({
    mutationFn: (id: number) => settlementService.confirmSettlement(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-balances", groupId] });
      queryClient.invalidateQueries({ queryKey: ["group-simplification-plan", groupId] });
      queryClient.invalidateQueries({
        queryKey: ["group-settlements", groupId],
      });
      toast.success("Payment confirmed");
    },
  });

  if (isLoading || isLoadingSettlements) {
    return (
      <div className="flex justify-center p-8">
        <Loader2 className="animate-spin text-blue-600" />
      </div>
    );
  }

  const isOwner = group?.createdBy === currentUserId;
  const currentMember = group?.members?.find((m) => m.userId === currentUserId);
  const isAdminOrOwner = isOwner || currentMember?.role === "ADMIN";

  const handleSettleFromPlan = (debt: { from: number; to: number; amount: number; toUsername: string }) => {
    setSelectedDebt(debt);
    setIsAddSettleModalOpen(true);
  };

  const userDebts =
    balances?.simplifiedDebts.filter((d) => d.from === currentUserId) || [];
  const userOwed =
    balances?.simplifiedDebts.filter((d) => d.to === currentUserId) || [];

  const pendingIncoming =
    settlements?.filter((s) => {
      const isGlobalPayment = !s.allocations || 
                              s.allocations.length === 0 || 
                              s.allocations.some(a => !a.groupId);
      return s.payeeId === currentUserId && s.status === "MARKED_PAID" && !isGlobalPayment;
    }) || [];

  const pendingOutgoing =
    settlements?.filter((s) => {
      const isGlobalPayment = !s.allocations || 
                              s.allocations.length === 0 || 
                              s.allocations.some(a => !a.groupId);
      return s.payerId === currentUserId && s.status === "MARKED_PAID" && !isGlobalPayment;
    }) || [];

  return (
    <div className="space-y-6">
      {/* Top Section: Suggested Debt Simplification Plan */}
      <DebtSimplificationPlanCard
        groupId={groupId}
        currentUserId={currentUserId}
        onSettleDebt={handleSettleFromPlan}
      />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="space-y-6">
          {/* You Owe */}
          <Card>
            <CardHeader>
              <CardTitle className="text-destructive">You Owe (Direct)</CardTitle>
            </CardHeader>
            <CardContent>
              {userDebts.length === 0 ? (
                <p className="text-muted-foreground text-sm italic">
                  You don't owe anything directly!
                </p>
              ) : (
                <ScrollArea className="h-[200px] pr-4">
                  <div className="space-y-3">
                    {userDebts.map((debt, idx) => (
                      <div key={idx} className="flex items-center justify-between">
                        <div>
                          <p className="text-sm font-medium">
                            To {debt.toUsername || `User ${debt.to}`}
                          </p>
                          <p className="text-lg font-bold text-destructive">
                            ${debt.amount.toFixed(2)}
                          </p>
                        </div>
                        <Button
                          size="sm"
                          onClick={() => {
                            setSelectedDebt({
                              ...debt,
                              toUsername: debt.toUsername || `User ${debt.to}`,
                            });
                            setIsAddSettleModalOpen(true);
                          }}
                        >
                          Settle
                        </Button>
                      </div>
                    ))}
                  </div>
                </ScrollArea>
              )}
            </CardContent>
          </Card>

          {/* You Are Owed */}
          <Card>
            <CardHeader>
              <CardTitle className="text-emerald-500">You Are Owed (Direct)</CardTitle>
            </CardHeader>
            <CardContent>
              {userOwed.length === 0 ? (
                <p className="text-muted-foreground text-sm italic">No one owes you directly.</p>
              ) : (
                <ScrollArea className="h-[200px] pr-4">
                  <div className="space-y-3">
                    {userOwed.map((debt, idx) => (
                      <div key={idx} className="flex items-center justify-between">
                        <div>
                          <p className="text-sm font-medium">
                            From {debt.fromUsername || `User ${debt.from}`}
                          </p>
                          <p className="text-lg font-bold text-emerald-500">
                            ${debt.amount.toFixed(2)}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                </ScrollArea>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">
          {/* Debt Simplification & Opt-Out Governance Card */}
          <SimplificationSettingsCard
            groupId={groupId}
            isAdminOrOwner={isAdminOrOwner}
            currentUserId={currentUserId}
          />

          {/* All Group Direct Debts */}
          <Card>
            <CardHeader>
              <CardTitle className="text-foreground flex items-center gap-2">
                <ArrowRight size={20} className="text-blue-500" />
                <span>All Raw Group Debts</span>
              </CardTitle>
            </CardHeader>
            <CardContent>
              {!balances?.simplifiedDebts ||
              balances.simplifiedDebts.length === 0 ? (
                <p className="text-muted-foreground text-sm italic">
                  No outstanding debts in this group.
                </p>
              ) : (
                <ScrollArea className="h-[220px] pr-4">
                  <div className="space-y-4">
                    {balances.simplifiedDebts.map((debt, idx) => (
                      <div
                        key={idx}
                        className="flex items-center gap-3 p-3 bg-muted rounded-lg border border-border"
                      >
                        <div className="flex-1">
                          <span className="font-semibold text-foreground">
                            {debt.fromUsername || `User ${debt.from}`}
                          </span>
                          <span className="mx-2 text-muted-foreground">owes</span>
                          <span className="font-semibold text-foreground">
                            {debt.toUsername || `User ${debt.to}`}
                          </span>
                        </div>
                        <div className="text-lg font-bold text-blue-500">
                          ${debt.amount.toFixed(2)}
                        </div>
                      </div>
                    ))}
                  </div>
                </ScrollArea>
              )}
            </CardContent>
          </Card>

          {/* Pending Confirmations */}
          {(pendingIncoming.length > 0 || pendingOutgoing.length > 0) && (
            <Card>
              <CardHeader>
                <CardTitle>Pending Confirmations</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  {pendingIncoming.map((s) => (
                    <div
                      key={s.id}
                      className="flex items-center justify-between p-3 bg-amber-50 rounded-lg border border-amber-100"
                    >
                      <div className="flex items-center gap-3">
                        <Clock className="text-amber-600" size={20} />
                        <div>
                          <p className="text-sm font-medium">
                            User {s.payerId} sent you ${s.amount.toFixed(2)}
                          </p>
                          <p className="text-xs text-amber-700">
                            Waiting for your confirmation
                          </p>
                        </div>
                      </div>
                      <Button
                        variant="default"
                        size="sm"
                        onClick={() => confirmMutation.mutate(s.id)}
                        disabled={confirmMutation.isPending}
                      >
                        Confirm Receipt
                      </Button>
                    </div>
                  ))}
                  {pendingOutgoing.map((s) => (
                    <div
                      key={s.id}
                      className="flex items-center gap-3 p-3 bg-blue-50 rounded-lg border border-blue-100"
                    >
                      <Clock className="text-blue-600" size={20} />
                      <div>
                        <p className="text-sm font-medium">
                          You sent ${s.amount.toFixed(2)} to User {s.payeeId}
                        </p>
                        <p className="text-xs text-blue-700">
                          Waiting for confirmation
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      </div>

      {/* Settle Modal */}
      <Dialog open={isSettleModalOpen} onOpenChange={(open) => { if (!open) setIsAddSettleModalOpen(false); }}>
        <DialogContent className="max-w-lg bg-background text-foreground border-border">
          <DialogHeader className="border-b border-border pb-3">
            <DialogTitle className="text-xl font-semibold">Record Payment</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 pt-2">
            <p className="text-muted-foreground">
              Confirm that you have sent{" "}
              <strong>${selectedDebt?.amount.toFixed(2)}</strong> to{" "}
              <strong>{selectedDebt?.toUsername}</strong>.
            </p>
            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="outline"
                onClick={() => setIsAddSettleModalOpen(false)}
              >
                Cancel
              </Button>
              <Button
                onClick={() =>
                  selectedDebt && createSettlementMutation.mutate(selectedDebt)
                }
                disabled={createSettlementMutation.isPending}
              >
                {createSettlementMutation.isPending
                  ? "Processing..."
                  : "Confirm & Mark Paid"}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default GroupBalances;
