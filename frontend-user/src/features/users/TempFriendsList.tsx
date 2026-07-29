import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { UserPlus, Loader2, AlertCircle, UserMinus } from "lucide-react";
import { Link } from "react-router-dom";
import { friendService } from "./friendService";
import { useAuthStore } from "../../store/authStore";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
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
    <Card className="border-orange-200 dark:border-orange-900/50 bg-orange-50/30 dark:bg-orange-900/10">
      <CardHeader>
        <CardTitle className="text-orange-800 dark:text-orange-400 flex items-center gap-2">
          <AlertCircle size={20} />
          Temporary Friends
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ScrollArea className="h-[300px] pr-4">
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
                className="flex items-center justify-between p-3 bg-background border border-orange-100 dark:border-orange-900/30 rounded-lg shadow-sm"
              >
                <Link
                  to={`/friends/${tf.userId}`}
                  className="flex-1 min-w-0 mr-4 group block hover:opacity-80 transition-opacity"
                >
                  <div className="flex items-center gap-2 mb-1">
                    <p className="font-semibold text-foreground truncate group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                      {tf.username}
                    </p>
                    <div className="flex flex-wrap gap-1">
                      {tf.groups.map((g) => (
                        <Badge
                          key={g.id}
                          className="text-[10px] py-0 px-1 bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400 border-orange-200 dark:border-orange-800/50"
                        >
                          {g.name}
                        </Badge>
                      ))}
                    </div>
                  </div>
                  <p
                    className={`text-sm ${tf.balance > 0 ? "text-emerald-500" : "text-destructive"}`}
                  >
                    {tf.balance > 0
                      ? `Owes you ${tf.balance.toFixed(2)}`
                      : `You owe ${Math.abs(tf.balance).toFixed(2)}`}
                  </p>
                </Link>

                {isPending ? (
                  <Button
                    size="sm"
                    variant="ghost"
                    className="flex items-center gap-1 text-red-600 dark:text-red-400 hover:text-red-700 dark:hover:text-red-300 hover:bg-red-50 dark:hover:bg-red-900/20"
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
                    variant="outline"
                    className="flex items-center gap-1 border-orange-200 dark:border-orange-800/50 hover:bg-orange-50 dark:hover:bg-orange-900/20 text-foreground"
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
        </ScrollArea>
      </CardContent>
    </Card>
  );
};

export default TempFriendsList;
