import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Check, X, Loader2 } from "lucide-react";
import api from "../../lib/axios";
import { friendService } from "./friendService";
import type { User, Friendship } from "../../types/user";
import Button from "../../components/core/Button/Button";
import { useAuthStore } from "../../store/authStore";

interface FriendRequestItemProps {
  request: Friendship;
  direction?: "INCOMING" | "OUTGOING";
}

const FriendRequestItem = ({
  request,
  direction = "INCOMING",
}: FriendRequestItemProps) => {
  const currentUser = useAuthStore((state) => state.user);
  const queryClient = useQueryClient();

  // For INCOMING, we want the requester. For OUTGOING, we want the addressee.
  const targetUserId =
    direction === "INCOMING" ? request.requesterId : request.addresseeId;

  // Fetch user details
  const { data: user, isLoading: isLoadingUser } = useQuery({
    queryKey: ["users", targetUserId],
    queryFn: async () => {
      const response = await api.get<User>(`/users/${targetUserId}`);
      return response.data;
    },
    enabled: !!targetUserId,
  });

  const respondMutation = useMutation({
    mutationFn: (action: "accept" | "reject") => {
      if (!currentUser?.id) throw new Error("User not logged in");
      return friendService.respondToFriendRequest(
        currentUser.id,
        request.id,
        action,
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["friend-requests", currentUser?.id],
      });
      queryClient.invalidateQueries({ queryKey: ["friends", currentUser?.id] });
    },
  });

  const cancelMutation = useMutation({
    mutationFn: () => {
      if (!currentUser?.id || !targetUserId) throw new Error("Missing ID");
      return friendService.removeFriend(currentUser.id, targetUserId);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["friend-requests", currentUser?.id],
      });
    },
  });

  if (isLoadingUser) {
    return (
      <div className="flex items-center justify-between p-3 bg-gray-50 rounded-md animate-pulse">
        <div className="h-4 w-32 bg-gray-200 rounded"></div>
        <div className="flex gap-2">
          <div className="h-8 w-8 bg-gray-200 rounded"></div>
          <div className="h-8 w-8 bg-gray-200 rounded"></div>
        </div>
      </div>
    );
  }

  if (!user) return null;

  return (
    <div className="flex items-center justify-between p-3 bg-white border border-gray-200 rounded-lg shadow-sm">
      <div>
        <p className="text-sm font-semibold text-gray-900">
          {user.firstName} {user.lastName}
        </p>
        <p className="text-xs text-gray-500">@{user.username}</p>
      </div>

      <div className="flex gap-2">
        {direction === "INCOMING" ? (
          <>
            <Button
              size="sm"
              variant="primary"
              onClick={() => respondMutation.mutate("accept")}
              disabled={respondMutation.isPending}
              title="Accept"
            >
              {respondMutation.isPending &&
              respondMutation.variables === "accept" ? (
                <Loader2 className="animate-spin" size={16} />
              ) : (
                <Check size={16} />
              )}
            </Button>
            <Button
              size="sm"
              variant="danger"
              onClick={() => respondMutation.mutate("reject")}
              disabled={respondMutation.isPending}
              title="Reject"
            >
              {respondMutation.isPending &&
              respondMutation.variables === "reject" ? (
                <Loader2 className="animate-spin text-blue-600" size={16} />
              ) : (
                <X size={16} />
              )}
            </Button>
          </>
        ) : (
          <Button
            size="sm"
            variant="danger"
            onClick={() => cancelMutation.mutate()}
            disabled={cancelMutation.isPending}
            title="Cancel Request"
          >
            {cancelMutation.isPending ? (
              <Loader2 className="animate-spin" size={16} />
            ) : (
              <>
                <X size={16} className="mr-1" />
                Cancel
              </>
            )}
          </Button>
        )}
      </div>
    </div>
  );
};

export default FriendRequestItem;
