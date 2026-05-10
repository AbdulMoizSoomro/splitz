import React, { useState } from "react";
import { Loader2 } from "lucide-react";
import { AxiosError } from "axios";
import Modal from "../../components/core/Modal/Modal";
import Button from "../../components/core/Button/Button";
import Input from "../../components/core/Input/Input";
import { friendService } from "./friendService";
import { useMutation, useQueryClient } from "@tanstack/react-query";
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
  const queryClient = useQueryClient();
  const { addToast } = useToastStore();

  const createMutation = useMutation({
    mutationFn: (data: { payerId: number; payeeId: number; amount: number }) =>
      friendService.createSettlement(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["friend-balance"] });
      addToast("Settlement recorded successfully", "success");
      onClose();
    },
    onError: (error: Error | AxiosError) => {
      const message =
        error instanceof AxiosError
          ? error.response?.data?.message
          : error.message;
      addToast(message || "Failed to record settlement", "error");
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const payerId = type === "PAY" ? currentUser.id : friend.id;
    const payeeId = type === "PAY" ? friend.id : currentUser.id;

    createMutation.mutate({
      payerId,
      payeeId,
      amount: parseFloat(amount),
    });
  };

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
            disabled={createMutation.isPending || parseFloat(amount) <= 0}
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
