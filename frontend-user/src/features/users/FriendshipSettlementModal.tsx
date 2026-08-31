import React, { useState, useEffect } from "react";
import { Loader2, ChevronDown, ChevronUp, AlertCircle } from "lucide-react";
import { AxiosError } from "axios";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { friendService } from "./friendService";
import { settlementService } from "../balances/settlementService";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { invalidations, bindInvalidations } from "../../lib/queryKeys";
import type { User } from "../../types/user";

import {
  autoAllocateSettlement,
  validateSettlementAllocation,
  buildSettlementPayload,
} from "./interpersonal";

interface FriendshipSettlementModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentUser: User;
  friend: User;
  suggestedAmount: number;
}

const FriendshipSettlementModal: React.FC<FriendshipSettlementModalProps> = ({
  isOpen,
  onClose,
  currentUser,
  friend,
  suggestedAmount,
}) => {
  const [amount, setAmount] = useState(Math.abs(suggestedAmount).toString());
  const [type, setType] = useState<"PAY" | "RECEIVE">(
    suggestedAmount < 0 ? "PAY" : "RECEIVE",
  );
  const [isAllocating, setIsAllocating] = useState(false);
  const [allocations, setAllocations] = useState<Record<number, string>>({});

  const queryClient = useQueryClient();
  const invalidate = bindInvalidations(queryClient);

  const { data: balanceData, isLoading: isLoadingBalance } = useQuery({
    queryKey: ["friend-balance", currentUser.id, friend.id],
    queryFn: () => friendService.getNetBalance(currentUser.id, friend.id),
    enabled: isOpen,
  });

  const sharedGroups = balanceData?.groupBalances || [];

  useEffect(() => {
    if (
      isAllocating &&
      sharedGroups.length > 0 &&
      Object.keys(allocations).length === 0
    ) {
      const autoAlloc = autoAllocateSettlement({
        amount: parseFloat(amount) || 0,
        type,
        groupBalances: sharedGroups,
      });

      const initialAllocations: Record<number, string> = {};
      Object.entries(autoAlloc).forEach(([groupId, val]) => {
        initialAllocations[parseInt(groupId)] = val.toFixed(2);
      });
      setAllocations(initialAllocations);
    }
  }, [isAllocating, sharedGroups, type, amount, allocations]);

  const validation = validateSettlementAllocation({
    amount: parseFloat(amount) || 0,
    isAllocating,
    allocations,
  });
  const totalAllocated = validation.totalAllocated;
  const isAllocationValid = validation.isValid;

  const createMutation = useMutation({
    mutationFn: (data: {
      payerId: number;
      payeeId: number;
      amount: number;
      allocations?: { groupId: number; amount: number }[];
    }) => settlementService.createSettlement(data),
    onSuccess: () => {
      invalidations.settlementMutated(invalidate, currentUser.id, friend.id);
      toast.success("Settlement recorded successfully");
      onClose();
    },
    onError: (error: Error | AxiosError) => {
      const message =
        error instanceof AxiosError
          ? error.response?.data?.detail || error.response?.data?.message
          : error.message;
      toast.error(message || "Failed to record settlement");
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const payload = buildSettlementPayload({
      currentUserId: currentUser.id,
      friendId: friend.id,
      type,
      amount: parseFloat(amount),
      isAllocating,
      allocations,
    });
    createMutation.mutate(payload);
  };

  const handleAllocationChange = (groupId: number, value: string) => {
    setAllocations((prev) => ({
      ...prev,
      [groupId]: value,
    }));
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="max-w-lg bg-background text-foreground border-border">
        <DialogHeader className="border-b border-border pb-3">
          <DialogTitle className="text-xl font-semibold">Settle Debt</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4 pt-2">
          <div className="flex bg-muted p-1 rounded-lg">
            <button
              type="button"
              className={`flex-1 py-2 px-4 rounded-md text-sm font-medium transition-colors cursor-pointer ${
                type === "PAY"
                  ? "bg-background shadow-sm text-blue-600 dark:text-blue-400"
                  : "text-muted-foreground hover:text-foreground"
              }`}
              onClick={() => setType("PAY")}
            >
              I Paid
            </button>
            <button
              type="button"
              className={`flex-1 py-2 px-4 rounded-md text-sm font-medium transition-colors cursor-pointer ${
                type === "RECEIVE"
                  ? "bg-background shadow-sm text-green-600 dark:text-green-400"
                  : "text-muted-foreground hover:text-foreground"
              }`}
              onClick={() => setType("RECEIVE")}
            >
              I Received
            </button>
          </div>

          <Field>
            <FieldLabel htmlFor="settlement-amount">
              {type === "PAY"
                ? `You paid ${friend.firstName}`
                : `${friend.firstName} paid you`}
            </FieldLabel>
            <Input
              id="settlement-amount"
              type="number"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              required
              className="text-lg font-semibold"
            />
          </Field>

          {sharedGroups.length > 0 && (
            <div className="border border-border rounded-lg overflow-hidden">
              <button
                type="button"
                className="w-full flex items-center justify-between p-3 bg-muted/50 hover:bg-muted transition-colors cursor-pointer text-foreground"
                onClick={() => setIsAllocating(!isAllocating)}
              >
                <span className="text-sm font-medium">
                  Allocate to group debts
                </span>
                {isAllocating ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
              </button>

              {isAllocating && (
                <div className="p-3 space-y-3 bg-background border-t border-border">
                  {isLoadingBalance ? (
                    <div className="flex justify-center py-4">
                      <Loader2 className="animate-spin text-gray-400" size={20} />
                    </div>
                  ) : (
                    <>
                      {sharedGroups.map((group) => {
                        const relevantBalance = Math.abs(group.balance);
                        const isOwed = (type === "PAY" && group.balance < 0) || 
                                      (type === "RECEIVE" && group.balance > 0);

                        return (
                          <div key={group.groupId} className="flex items-center gap-3">
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-medium text-foreground truncate">
                                {group.groupName}
                              </p>
                              <p className={`text-xs ${isOwed ? "text-orange-600 dark:text-orange-400" : "text-muted-foreground"}`}>
                                {group.balance < 0 ? "You owe" : "Owes you"} €{relevantBalance.toFixed(2)}
                              </p>
                            </div>
                            <div className="w-32">
                              <Input
                                type="number"
                                step="0.01"
                                placeholder="0.00"
                                value={allocations[group.groupId] || ""}
                                onChange={(e) => handleAllocationChange(group.groupId, e.target.value)}
                                className="text-right text-sm"
                                aria-label={`Allocate to ${group.groupName}`}
                              />
                            </div>
                          </div>
                        );
                      })}

                      <div className="pt-2 border-t border-border flex justify-between items-center">
                        <span className="text-sm font-medium text-foreground">Total Allocated:</span>
                        <span className={`text-sm font-bold ${isAllocationValid ? "text-green-600 dark:text-green-400" : "text-red-600 dark:text-red-400"}`}>
                          €{totalAllocated.toFixed(2)} / €{parseFloat(amount || "0").toFixed(2)}
                        </span>
                      </div>

                      {!isAllocationValid && (
                        <div className="space-y-2">
                          <div className="flex items-center gap-2 text-xs text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 p-2 rounded border border-red-100 dark:border-red-900/50">
                            <AlertCircle size={14} className="shrink-0" />
                            <span>Allocated sum must match the total amount</span>
                          </div>
                          <p className="text-[11px] text-muted-foreground leading-relaxed px-1">
                            💡 <strong>Tip:</strong> Manual allocations must sum to the total payment. 
                            If you want to resolve outstanding group debts first and have any leftover amount automatically flow into your <strong>Direct Direct Personal Balance</strong>, simply turn off "Allocate to group debts" above.
                          </p>
                        </div>
                      )}
                    </>
                  )}
                </div>
              )}
            </div>
          )}

          <div className="pt-4 flex gap-3">
            <Button
              variant="outline"
              className="flex-1"
              onClick={onClose}
              type="button"
            >
              Cancel
            </Button>
            <Button
              variant="default"
              className="flex-1"
              type="submit"
              disabled={
                createMutation.isPending || 
                parseFloat(amount) <= 0 || 
                !isAllocationValid
              }
            >
              {createMutation.isPending ? (
                <Loader2 className="animate-spin" size={18} />
              ) : (
                "Save Settlement"
              )}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
};

export default FriendshipSettlementModal;
