import { useState, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useInterpersonalFriend } from "./interpersonal";
import { useAuthStore } from "../../store/authStore";
import { toast } from "sonner";
import type { FriendshipSettlementDTO } from "../../types/user";

export function useFriendDetailSession() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const friendId = Number(id);
  const { user: currentUser } = useAuthStore();

  const [isSettlementModalOpen, setIsSettlementModalOpen] = useState(false);
  const [editingSettlementId, setEditingSettlementId] = useState<number | null>(
    null,
  );
  const [editAmount, setEditAmount] = useState("");
  const [isAddFriendModalOpen, setIsAddFriendModalOpen] = useState(false);
  const [isCancelRequestModalOpen, setIsCancelRequestModalOpen] =
    useState(false);

  const {
    friend,
    relationshipStatus,
    netBalance,
    directBalance,
    groupBalances,
    sharedGroups,
    activityFeed: unifiedActivity,
    isLoading,
    mutations,
  } = useInterpersonalFriend(friendId);

  const isConfirmedFriend = relationshipStatus === "CONFIRMED_FRIEND";
  const isPendingOutgoing = relationshipStatus === "PENDING_OUTGOING";
  const hasLoadedStatus = relationshipStatus !== undefined;

  const groupNameMap = useMemo(() => {
    const map: Record<number, string> = {};
    sharedGroups.forEach((g) => {
      map[g.id] = g.name;
    });
    return map;
  }, [sharedGroups]);

  const groupBalancesMap = useMemo(() => {
    const map: Record<number, number> = {};
    groupBalances.forEach((gb) => {
      map[gb.groupId] = gb.balance;
    });
    return map;
  }, [groupBalances]);

  const handleAddFriend = () => {
    mutations.sendFriendRequest.mutate(undefined, {
      onSuccess: () => {
        setIsAddFriendModalOpen(false);
        toast.success("Friend request sent");
      },
    });
  };

  const handleCancelRequest = () => {
    mutations.cancelFriendRequest.mutate(undefined, {
      onSuccess: () => {
        setIsCancelRequestModalOpen(false);
        toast.success("Friend request cancelled");
      },
    });
  };

  const handleConfirmSettlement = (settlementId: number) => {
    mutations.confirmSettlement.mutate(settlementId, {
      onSuccess: () => toast.success("Payment confirmed"),
      onError: () => toast.error("Failed to confirm payment"),
    });
  };

  const handleUpdateSettlement = (
    settlementId: number,
    amount: number,
  ) => {
    mutations.updateSettlement.mutate(
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
    friendId,
    navigate,
    currentUser,
    friend,
    relationshipStatus,
    isConfirmedFriend,
    isPendingOutgoing,
    hasLoadedStatus,
    netBalance,
    directBalance,
    groupBalances,
    sharedGroups,
    unifiedActivity,
    isLoading,
    groupNameMap,
    groupBalancesMap,
    isSettlementModalOpen,
    setIsSettlementModalOpen,
    editingSettlementId,
    setEditingSettlementId,
    editAmount,
    setEditAmount,
    isAddFriendModalOpen,
    setIsAddFriendModalOpen,
    isCancelRequestModalOpen,
    setIsCancelRequestModalOpen,
    handleAddFriend,
    handleCancelRequest,
    handleConfirmSettlement,
    handleUpdateSettlement,
    startEditing,
    cancelEditing,
    submitEdit,
    sendFriendRequestPending: mutations.sendFriendRequest.isPending,
    cancelFriendRequestPending: mutations.cancelFriendRequest.isPending,
    confirmSettlementPending: mutations.confirmSettlement.isPending,
    updateSettlementPending: mutations.updateSettlement.isPending,
  };
}

export type FriendDetailSession = ReturnType<typeof useFriendDetailSession>;
