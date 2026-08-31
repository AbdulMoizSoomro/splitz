import { useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useInterpersonalFriend } from "./interpersonal";
import { useFriendRelationship } from "./useFriendRelationship";
import { useFriendSettlementEditing } from "./useFriendSettlementEditing";
import { useAuthStore } from "../../store/authStore";

export function useFriendDetailSession() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const friendId = Number(id);
  const { user: currentUser } = useAuthStore();

  const interpersonal = useInterpersonalFriend(friendId);
  const relationship = useFriendRelationship(interpersonal);
  const settlementEditing = useFriendSettlementEditing(interpersonal);

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
  } = interpersonal;

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
    isSettlementModalOpen: settlementEditing.isSettlementModalOpen,
    setIsSettlementModalOpen: settlementEditing.setIsSettlementModalOpen,
    editingSettlementId: settlementEditing.editingSettlementId,
    setEditingSettlementId: settlementEditing.setEditingSettlementId,
    editAmount: settlementEditing.editAmount,
    setEditAmount: settlementEditing.setEditAmount,
    isAddFriendModalOpen: relationship.isAddFriendModalOpen,
    setIsAddFriendModalOpen: relationship.setIsAddFriendModalOpen,
    isCancelRequestModalOpen: relationship.isCancelRequestModalOpen,
    setIsCancelRequestModalOpen: relationship.setIsCancelRequestModalOpen,
    handleAddFriend: relationship.handleAddFriend,
    handleCancelRequest: relationship.handleCancelRequest,
    handleConfirmSettlement: settlementEditing.handleConfirmSettlement,
    handleUpdateSettlement: settlementEditing.handleUpdateSettlement,
    startEditing: settlementEditing.startEditing,
    cancelEditing: settlementEditing.cancelEditing,
    submitEdit: settlementEditing.submitEdit,
    sendFriendRequestPending: mutations.sendFriendRequest.isPending,
    cancelFriendRequestPending: mutations.cancelFriendRequest.isPending,
    confirmSettlementPending: mutations.confirmSettlement.isPending,
    updateSettlementPending: mutations.updateSettlement.isPending,
  };
}

export type FriendDetailSession = ReturnType<typeof useFriendDetailSession>;
