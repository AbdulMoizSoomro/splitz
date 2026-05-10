import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { UserPlus, Loader2, AlertCircle, UserMinus } from "lucide-react";
import { friendService } from "./friendService";
import { useAuthStore } from "../../store/authStore";
import Button from "../../components/core/Button/Button";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "../../components/core/Card/Card";
import Badge from "../../components/core/Badge/Badge";
import { useTempFriends } from "../../hooks/useTempFriends";

const TempFriendsList = () => {
  const user = useAuthStore((state) => state.user);
  const currentUserId = Number(user?.id);
  const queryClient = useQueryClient();

  const { tempFriends, isLoading: isLoadingTemp } = useTempFriends();

  // Fetch outgoing requests to check status
  const { data: outgoingRequests, isLoading: isLoadingRequests } = useQuery({
    queryKey: ["friend-requests", currentUserId, "OUTGOING"],
    queryFn: () => friendService.getFriendRequests(currentUserId, "OUTGOING"),
    enabled: !!currentUserId,
  });

  // Add friend mutation
  const addFriendMutation = useMutation({
    mutationFn: (friendId: number) =>
      friendService.sendFriendRequest(currentUserId, friendId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["friend-requests", currentUserId],
      });
    },
  });

  // Cancel friend request mutation
  const cancelRequestMutation = useMutation({
    mutationFn: (friendId: number) =>
      friendService.removeFriend(currentUserId, friendId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["friend-requests", currentUserId],
      });
    },
  });

  const isLoading = isLoadingTemp || isLoadingRequests;

  if (isLoading) {
    return (
      <div className="flex justify-center py-4">
        <Loader2 className="animate-spin text-blue-600" size={24} />
      </div>
    );
  }

  if (tempFriends.length === 0) {
    return null;
  }

  return (
    <Card className="border-orange-200 bg-orange-50/30">
      <CardHeader>
        <CardTitle className="text-orange-800 flex items-center gap-2">
          <AlertCircle size={20} />
          Temporary Friends
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          {tempFriends.map((tf) => {
            const pendingRequest = outgoingRequests?.find(
              (r) => r.addresseeId === tf.userId,
            );
            const isPending = !!pendingRequest;
            const isMutationPending =
              addFriendMutation.isPending || cancelRequestMutation.isPending;

            return (
              <div
                key={tf.userId}
                className="flex items-center justify-between p-3 bg-white border border-orange-100 rounded-lg shadow-sm"
              >
                <div className="flex-1 min-w-0 mr-4">
                  <div className="flex items-center gap-2 mb-1">
                    <p className="font-semibold text-gray-900 truncate">
                      {tf.username}
                    </p>
                    <div className="flex flex-wrap gap-1">
                      {tf.groups.map((g) => (
                        <Badge
                          key={g.id}
                          variant="default"
                          className="text-[10px] py-0 px-1 bg-orange-100 text-orange-700 border-orange-200"
                        >
                          {g.name}
                        </Badge>
                      ))}
                    </div>
                  </div>
                  <p
                    className={`text-sm ${tf.balance > 0 ? "text-green-600" : "text-red-600"}`}
                  >
                    {tf.balance > 0
                      ? `Owes you ${tf.balance.toFixed(2)}`
                      : `You owe ${Math.abs(tf.balance).toFixed(2)}`}
                  </p>
                </div>

                {isPending ? (
                  <Button
                    size="sm"
                    variant="ghost"
                    className="flex items-center gap-1 text-red-600 hover:text-red-700 hover:bg-red-50"
                    onClick={() => cancelRequestMutation.mutate(tf.userId)}
                    disabled={isMutationPending}
                  >
                    {cancelRequestMutation.isPending ? (
                      <Loader2 className="animate-spin" size={16} />
                    ) : (
                      <UserMinus size={16} />
                    )}
                    <span>
                      {cancelRequestMutation.isPending
                        ? "Cancelling..."
                        : "Cancel Request"}
                    </span>
                  </Button>
                ) : (
                  <Button
                    size="sm"
                    variant="secondary"
                    className="flex items-center gap-1 border-orange-200 hover:bg-orange-50"
                    onClick={() => addFriendMutation.mutate(tf.userId)}
                    disabled={isMutationPending}
                  >
                    {addFriendMutation.isPending ? (
                      <Loader2 className="animate-spin" size={16} />
                    ) : (
                      <UserPlus size={16} />
                    )}
                    <span>
                      {addFriendMutation.isPending
                        ? "Sending..."
                        : "Add Friend"}
                    </span>
                  </Button>
                )}
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
};

export default TempFriendsList;
