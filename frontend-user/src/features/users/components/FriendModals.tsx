import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import FriendshipSettlementModal from "../FriendshipSettlementModal";
import type { FriendDetailSession } from "../useFriendDetailSession";

interface FriendModalsProps {
  session: FriendDetailSession;
}

export function FriendModals({ session }: FriendModalsProps) {
  const {
    currentUser,
    friend,
    netBalance,
    isSettlementModalOpen,
    setIsSettlementModalOpen,
    isAddFriendModalOpen,
    setIsAddFriendModalOpen,
    isCancelRequestModalOpen,
    setIsCancelRequestModalOpen,
    handleAddFriend,
    handleCancelRequest,
    sendFriendRequestPending,
    cancelFriendRequestPending,
  } = session;

  return (
    <>
      {isSettlementModalOpen && currentUser && friend && (
        <FriendshipSettlementModal
          isOpen={isSettlementModalOpen}
          onClose={() => setIsSettlementModalOpen(false)}
          currentUser={{
            id: Number(currentUser.id),
            username: currentUser.username,
            email: currentUser.email,
            firstName: currentUser.username,
            lastName: "",
          }}
          friend={friend}
          suggestedAmount={netBalance}
        />
      )}

      {friend && (
        <>
          <Dialog
            open={isAddFriendModalOpen}
            onOpenChange={(open) => {
              if (!open) setIsAddFriendModalOpen(false);
            }}
          >
            <DialogContent className="sm:max-w-[425px]">
              <DialogHeader>
                <DialogTitle>Add Friend</DialogTitle>
                <DialogDescription>
                  Send a friend request to {friend.firstName} {friend.lastName}?
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button
                  variant="outline"
                  onClick={() => setIsAddFriendModalOpen(false)}
                >
                  Cancel
                </Button>
                <Button
                  onClick={handleAddFriend}
                  disabled={sendFriendRequestPending}
                >
                  {sendFriendRequestPending ? "Sending..." : "Send Request"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>

          <Dialog
            open={isCancelRequestModalOpen}
            onOpenChange={(open) => {
              if (!open) setIsCancelRequestModalOpen(false);
            }}
          >
            <DialogContent className="sm:max-w-[425px]">
              <DialogHeader>
                <DialogTitle>Cancel Friend Request</DialogTitle>
                <DialogDescription>
                  Are you sure you want to cancel the friend request sent to{" "}
                  {friend.firstName} {friend.lastName}?
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button
                  variant="outline"
                  onClick={() => setIsCancelRequestModalOpen(false)}
                >
                  Keep Request
                </Button>
                <Button
                  variant="destructive"
                  onClick={handleCancelRequest}
                  disabled={cancelFriendRequestPending}
                >
                  {cancelFriendRequestPending ? "Cancelling..." : "Cancel Request"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </>
      )}
    </>
  );
}
