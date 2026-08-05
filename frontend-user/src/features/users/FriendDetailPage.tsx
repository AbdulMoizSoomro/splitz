import DashboardLayout from "../../components/layout/DashboardLayout";
import { Loader2 } from "lucide-react";
import { useFriendDetailSession } from "./useFriendDetailSession";
import { FriendHeader } from "./components/FriendHeader";
import { FriendBalanceBanner } from "./components/FriendBalanceBanner";
import { FriendContactCard } from "./components/FriendContactCard";
import { FriendMutualGroupsTab } from "./components/FriendMutualGroupsTab";
import { FriendActivityTab } from "./components/FriendActivityTab";
import { FriendModals } from "./components/FriendModals";

const FriendDetailPage = () => {
  const session = useFriendDetailSession();
  const {
    friend,
    isLoading,
    hasLoadedStatus,
    isConfirmedFriend,
    isPendingOutgoing,
    netBalance,
    directBalance,
    sharedGroups,
    groupBalancesMap,
    unifiedActivity,
    currentUser,
    friendId,
    editingSettlementId,
    editAmount,
    groupNameMap,
    startEditing,
    cancelEditing,
    submitEdit,
    handleConfirmSettlement,
    confirmSettlementPending,
    updateSettlementPending,
    setEditAmount,
    setIsSettlementModalOpen,
    setIsAddFriendModalOpen,
    setIsCancelRequestModalOpen,
  } = session;

  if (isLoading) {
    return (
      <DashboardLayout>
        <div className="flex justify-center py-12">
          <Loader2 className="animate-spin text-blue-600" size={40} />
        </div>
      </DashboardLayout>
    );
  }

  if (!friend) {
    return (
      <DashboardLayout>
        <div className="text-center py-12">
          <p className="text-muted-foreground">Friend not found.</p>
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout
      breadcrumbs={[
        { label: "Friends", href: "/friends" },
        { label: `${friend.firstName} ${friend.lastName}` },
      ]}
    >
      <div className="space-y-6">
        <FriendHeader
          friend={friend}
          hasLoadedStatus={hasLoadedStatus}
          isConfirmedFriend={isConfirmedFriend}
          isPendingOutgoing={isPendingOutgoing}
          onOpenAddFriendModal={() => setIsAddFriendModalOpen(true)}
          onOpenCancelRequestModal={() => setIsCancelRequestModalOpen(true)}
        />

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-1 space-y-6">
            <FriendBalanceBanner
              friend={friend}
              netBalance={netBalance}
              directBalance={directBalance}
              sharedGroups={sharedGroups}
              groupBalancesMap={groupBalancesMap}
              onOpenSettlementModal={() => setIsSettlementModalOpen(true)}
            />

            <FriendContactCard friend={friend} />
          </div>

          <div className="lg:col-span-2 space-y-6">
            <FriendMutualGroupsTab sharedGroups={sharedGroups} />

            <FriendActivityTab
              unifiedActivity={unifiedActivity}
              currentUserId={Number(currentUser?.id)}
              friend={friend}
              friendId={friendId}
              editingSettlementId={editingSettlementId}
              editAmount={editAmount}
              groupNameMap={groupNameMap}
              onStartEditing={startEditing}
              onCancelEditing={cancelEditing}
              onSubmitEdit={submitEdit}
              onConfirmSettlement={handleConfirmSettlement}
              confirmSettlementPending={confirmSettlementPending}
              updateSettlementPending={updateSettlementPending}
              onSetEditAmount={setEditAmount}
            />
          </div>
        </div>
      </div>

      <FriendModals session={session} />
    </DashboardLayout>
  );
};

export default FriendDetailPage;
