import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { UserCheck, UserPlus, Loader2 } from "lucide-react";
import { friendService } from "./friendService";
import { useAuthStore } from "../../store/authStore";
import FriendRequestItem from "./FriendRequestItem";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";

const FriendRequestsList = () => {
  const currentUser = useAuthStore((state) => state.user);
  const [direction, setDirection] = useState<"INCOMING" | "OUTGOING">(
    "INCOMING",
  );

  const { data: requests, isLoading } = useQuery({
    queryKey: ["friend-requests", currentUser?.id, direction],
    queryFn: () => {
      if (!currentUser?.id) return Promise.resolve([]);
      return friendService.getFriendRequests(currentUser.id, direction);
    },
    enabled: !!currentUser?.id,
  });

  if (isLoading) {
    return (
      <div className="flex justify-center py-4">
        <Loader2 className="animate-spin text-blue-600" size={24} />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex border-b border-border">
        <button
          className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
            direction === "INCOMING"
              ? "border-blue-600 text-blue-600"
              : "border-transparent text-muted-foreground hover:text-foreground hover:border-border"
          }`}
          onClick={() => setDirection("INCOMING")}
        >
          Received
        </button>
        <button
          className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
            direction === "OUTGOING"
              ? "border-blue-600 text-blue-600"
              : "border-transparent text-muted-foreground hover:text-foreground hover:border-border"
          }`}
          onClick={() => setDirection("OUTGOING")}
        >
          Sent
        </button>
      </div>

      {!requests || requests.length === 0 ? (
        <Card className="text-center py-6 bg-muted/50 border border-dashed border-border shadow-none">
          <CardContent className="pt-6">
            {direction === "INCOMING" ? (
              <UserCheck className="mx-auto text-muted-foreground mb-2" size={32} />
            ) : (
              <UserPlus className="mx-auto text-muted-foreground mb-2" size={32} />
            )}
            <p className="text-sm text-muted-foreground">
              {direction === "INCOMING"
                ? "No pending friend requests."
                : "No pending sent requests."}
            </p>
          </CardContent>
        </Card>
      ) : (
        <ScrollArea className="h-[300px] pr-4">
          <div className="space-y-3">
            {requests.map((request) => (
              <FriendRequestItem
                key={request.id}
                request={request}
                direction={direction}
              />
            ))}
          </div>
        </ScrollArea>
      )}
    </div>
  );
};

export default FriendRequestsList;
