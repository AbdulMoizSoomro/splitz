import { Button } from "@/components/ui/button";
import { ArrowLeft, User as UserIcon, UserPlus, UserMinus } from "lucide-react";
import { useNavigate } from "react-router-dom";
import type { User } from "../../../types/user";

interface FriendHeaderProps {
  friend: User;
  hasLoadedStatus: boolean;
  isConfirmedFriend: boolean;
  isPendingOutgoing: boolean;
  onOpenAddFriendModal: () => void;
  onOpenCancelRequestModal: () => void;
}

export function FriendHeader({
  friend,
  hasLoadedStatus,
  isConfirmedFriend,
  isPendingOutgoing,
  onOpenAddFriendModal,
  onOpenCancelRequestModal,
}: FriendHeaderProps) {
  const navigate = useNavigate();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <Button
          variant="ghost"
          size="sm"
          className="flex items-center gap-2 text-muted-foreground"
          onClick={() => navigate("/friends")}
        >
          <ArrowLeft size={18} />
          Back to Friends
        </Button>

        {hasLoadedStatus && !isConfirmedFriend && (
          isPendingOutgoing ? (
            <Button
              variant="outline"
              size="sm"
              onClick={onOpenCancelRequestModal}
            >
              <UserMinus size={16} className="mr-2" />
              Cancel Request
            </Button>
          ) : (
            <Button
              variant="default"
              size="sm"
              onClick={onOpenAddFriendModal}
            >
              <UserPlus size={16} className="mr-2" />
              Add Friend
            </Button>
          )
        )}
      </div>

      <div className="flex items-center gap-4">
        <div className="w-16 h-16 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 text-2xl font-bold">
          {friend.firstName ? friend.firstName[0] : ""}
          {friend.lastName ? friend.lastName[0] : ""}
        </div>
        <div>
          <h1 className="text-3xl font-bold text-foreground">
            {friend.firstName} {friend.lastName}
          </h1>
          <p className="text-muted-foreground flex items-center gap-1">
            <UserIcon size={16} /> @{friend.username}
          </p>
        </div>
      </div>
    </div>
  );
}
