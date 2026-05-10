import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { UserCheck, UserPlus, Loader2 } from "lucide-react";
import { friendService } from "./friendService";
import { useAuthStore } from "../../store/authStore";
import FriendRequestItem from "./FriendRequestItem";

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
      <div className="flex border-b border-gray-200">
        <button
          className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
            direction === "INCOMING"
              ? "border-blue-600 text-blue-600"
              : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
          }`}
          onClick={() => setDirection("INCOMING")}
        >
          Received
        </button>
        <button
          className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
            direction === "OUTGOING"
              ? "border-blue-600 text-blue-600"
              : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
          }`}
          onClick={() => setDirection("OUTGOING")}
        >
          Sent
        </button>
      </div>

      {!requests || requests.length === 0 ? (
        <div className="text-center py-6 bg-gray-50 border border-dashed border-gray-300 rounded-lg">
          {direction === "INCOMING" ? (
            <UserCheck className="mx-auto text-gray-400 mb-2" size={32} />
          ) : (
            <UserPlus className="mx-auto text-gray-400 mb-2" size={32} />
          )}
          <p className="text-sm text-gray-500">
            {direction === "INCOMING"
              ? "No pending friend requests."
              : "No pending sent requests."}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {requests.map((request) => (
            <FriendRequestItem
              key={request.id}
              request={request}
              direction={direction}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default FriendRequestsList;
