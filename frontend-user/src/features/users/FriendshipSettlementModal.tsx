import React, { useState } from "react";
import { Loader2, ChevronDown, ChevronUp, AlertCircle } from "lucide-react";
import { AxiosError } from "axios";
import Modal from "../../components/core/Modal/Modal";
import Button from "../../components/core/Button/Button";
import Input from "../../components/core/Input/Input";
import { friendService } from "./friendService";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useToastStore } from "../../store/toastStore";
import type { User } from "../../types/user";

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
  const { addToast } = useToastStore();

  const { data: balanceData, isLoading: isLoadingBalance } = useQuery({
    queryKey: ["friend-balance", currentUser.id, friend.id],
    queryFn: () => friendService.getNetBalance(currentUser.id, friend.id),
    enabled: isOpen,
  });

  const sharedGroups = balanceData?.groupBalances || [];

  const totalAllocated = Object.values(allocations).reduce(
    (sum, val) => sum + (parseFloat(val) || 0),
    0,
  );

  const createMutation = useMutation({
    mutationFn: (data: {
      payerId: number;
      payeeId: number;
      amount: number;
      allocations?: { groupId: number; amount: number }[];
    }) => friendService.createSettlement(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["friend-balance"] });
      queryClient.invalidateQueries({ queryKey: ["friend-settlements"] });
      addToast("Settlement recorded successfully", "success");
      onClose();
    },
    onError: (error: Error | AxiosError) => {
      const message =
        error instanceof AxiosError
          ? error.response?.data?.detail || error.response?.data?.message
          : error.message;
      addToast(message || "Failed to record settlement", "error");
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const payerId = type === "PAY" ? currentUser.id : friend.id;
    const payeeId = type === "PAY" ? friend.id : currentUser.id;
    const finalAmount = parseFloat(amount);

    const payload: {
      payerId: number;
      payeeId: number;
      amount: number;
      allocations?: { groupId: number; amount: number }[];
    } = {
      payerId,
      payeeId,
      amount: finalAmount,
    };

    if (isAllocating) {
      payload.allocations = Object.entries(allocations)
        .filter(([, val]) => parseFloat(val) > 0)
        .map(([groupId, val]) => ({
          groupId: parseInt(groupId),
          amount: parseFloat(val),
        }));
    }

    createMutation.mutate(payload);
  };

  const handleAllocationChange = (groupId: number, value: string) => {
    setAllocations((prev) => ({
      ...prev,
      [groupId]: value,
    }));
  };

  const isAllocationValid = !isAllocating || Math.abs(totalAllocated - parseFloat(amount)) < 0.01;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Settle Debt">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="flex bg-gray-100 p-1 rounded-lg">
          <button
            type="button"
            className={`flex-1 py-2 px-4 rounded-md text-sm font-medium transition-colors ${
              type === "PAY"
                ? "bg-white shadow-sm text-blue-600"
                : "text-gray-600 hover:text-gray-900"
            }`}
            onClick={() => setType("PAY")}
          >
            I Paid
          </button>
          <button
            type="button"
            className={`flex-1 py-2 px-4 rounded-md text-sm font-medium transition-colors ${
              type === "RECEIVE"
                ? "bg-white shadow-sm text-green-600"
                : "text-gray-600 hover:text-gray-900"
            }`}
            onClick={() => setType("RECEIVE")}
          >
            I Received
          </button>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {type === "PAY"
              ? `You paid ${friend.firstName}`
              : `${friend.firstName} paid you`}
          </label>
          <Input
            type="number"
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="0.00"
            required
            className="text-lg font-semibold"
          />
        </div>

        {sharedGroups.length > 0 && (
          <div className="border rounded-lg overflow-hidden">
            <button
              type="button"
              className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100 transition-colors"
              onClick={() => setIsAllocating(!isAllocating)}
            >
              <span className="text-sm font-medium text-gray-700">
                Allocate to group debts
              </span>
              {isAllocating ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
            </button>

            {isAllocating && (
              <div className="p-3 space-y-3 bg-white border-t">
                {isLoadingBalance ? (
                  <div className="flex justify-center py-4">
                    <Loader2 className="animate-spin text-gray-400" size={20} />
                  </div>
                ) : (
                  <>
                    {sharedGroups.map((group) => {
                      // Adjust balance based on who is paying
                      // If I'm PAYING, I want to settle my debt (negative balance)
                      // If I'm RECEIVING, I want to settle their debt (positive balance)
                      const relevantBalance = Math.abs(group.balance);
                      const isOwed = (type === "PAY" && group.balance < 0) || 
                                    (type === "RECEIVE" && group.balance > 0);

                      return (
                        <div key={group.groupId} className="flex items-center gap-3">
                          <div className="flex-1 min-w-0">
                            <p className="text-sm font-medium text-gray-900 truncate">
                              {group.groupName}
                            </p>
                            <p className={`text-xs ${isOwed ? "text-orange-600" : "text-gray-500"}`}>
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

                    <div className="pt-2 border-t flex justify-between items-center">
                      <span className="text-sm font-medium text-gray-700">Total Allocated:</span>
                      <span className={`text-sm font-bold ${isAllocationValid ? "text-green-600" : "text-red-600"}`}>
                        €{totalAllocated.toFixed(2)} / €{parseFloat(amount || "0").toFixed(2)}
                      </span>
                    </div>

                    {!isAllocationValid && (
                      <div className="flex items-center gap-2 text-xs text-red-600 bg-red-50 p-2 rounded">
                        <AlertCircle size={14} />
                        <span>Allocated sum must match the total amount</span>
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
            variant="secondary"
            className="flex-1"
            onClick={onClose}
            type="button"
          >
            Cancel
          </Button>
          <Button
            variant="primary"
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
    </Modal>
  );
};

export default FriendshipSettlementModal;
