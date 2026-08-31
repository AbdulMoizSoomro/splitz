import { useState } from "react";
import { toast } from "sonner";
import type { InterpersonalResult } from "./interpersonal";

export function useFriendRelationship(interpersonal: InterpersonalResult) {
  const [isAddFriendModalOpen, setIsAddFriendModalOpen] = useState(false);
  const [isCancelRequestModalOpen, setIsCancelRequestModalOpen] =
    useState(false);

  const handleAddFriend = () => {
    interpersonal.mutations.sendFriendRequest.mutate(undefined, {
      onSuccess: () => {
        setIsAddFriendModalOpen(false);
        toast.success("Friend request sent");
      },
    });
  };

  const handleCancelRequest = () => {
    interpersonal.mutations.cancelFriendRequest.mutate(undefined, {
      onSuccess: () => {
        setIsCancelRequestModalOpen(false);
        toast.success("Friend request cancelled");
      },
    });
  };

  return {
    isAddFriendModalOpen,
    setIsAddFriendModalOpen,
    isCancelRequestModalOpen,
    setIsCancelRequestModalOpen,
    handleAddFriend,
    handleCancelRequest,
  };
}
