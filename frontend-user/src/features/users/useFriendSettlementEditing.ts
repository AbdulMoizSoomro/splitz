import { useState } from "react";
import { toast } from "sonner";
import type { FriendshipSettlementDTO } from "../../types/user";
import type { InterpersonalResult } from "./interpersonal";

export function useFriendSettlementEditing(interpersonal: InterpersonalResult) {
  const [isSettlementModalOpen, setIsSettlementModalOpen] = useState(false);
  const [editingSettlementId, setEditingSettlementId] = useState<number | null>(
    null,
  );
  const [editAmount, setEditAmount] = useState("");

  const handleConfirmSettlement = (settlementId: number) => {
    interpersonal.mutations.confirmSettlement.mutate(settlementId, {
      onSuccess: () => toast.success("Payment confirmed"),
      onError: () => toast.error("Failed to confirm payment"),
    });
  };

  const handleUpdateSettlement = (settlementId: number, amount: number) => {
    interpersonal.mutations.updateSettlement.mutate(
      { settlementId, amount },
      {
        onSuccess: () => {
          setEditingSettlementId(null);
          setEditAmount("");
          toast.success("Payment updated");
        },
        onError: () => toast.error("Failed to update payment"),
      },
    );
  };

  const startEditing = (settlement: FriendshipSettlementDTO) => {
    setEditingSettlementId(settlement.id);
    setEditAmount(settlement.amount.toFixed(2));
  };

  const cancelEditing = () => {
    setEditingSettlementId(null);
    setEditAmount("");
  };

  const submitEdit = (settlementId: number) => {
    const parsedAmount = parseFloat(editAmount);
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      toast.error("Please enter a valid amount");
      return;
    }
    handleUpdateSettlement(settlementId, parsedAmount);
  };

  return {
    isSettlementModalOpen,
    setIsSettlementModalOpen,
    editingSettlementId,
    setEditingSettlementId,
    editAmount,
    setEditAmount,
    handleConfirmSettlement,
    handleUpdateSettlement,
    startEditing,
    cancelEditing,
    submitEdit,
  };
}
