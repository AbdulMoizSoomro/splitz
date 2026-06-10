import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, Loader2, UserPlus, CheckCircle2 } from "lucide-react";
import api from "../../lib/axios";
import { friendService } from "../users/friendService";
import { useAuthStore } from "../../store/authStore";
import { useTempFriends } from "../../hooks/useTempFriends";
import type { User, PaginatedResponse } from "../../types/user";
import { Badge } from "@/components/ui/badge";

interface MemberPickerProps {
  selectedIds: number[];
  onToggle: (id: number) => void;
  excludeIds?: number[];
  placeholder?: string;
}

interface DisplayUser {
  id: number;
  firstName: string;
  lastName?: string;
  username: string;
  type: "friend" | "temp-friend" | "search-result" | "self";
}

const MemberPicker = ({
  selectedIds,
  onToggle,
  excludeIds = [],
  placeholder = "Search friends or users...",
}: MemberPickerProps) => {
  const [searchQuery, setSearchQuery] = useState("");
  const currentUser = useAuthStore((state) => state.user);
  const currentUserId = Number(currentUser?.id);

  // 1. Fetch Friends
  const { data: friends, isLoading: isLoadingFriends } = useQuery({
    queryKey: ["friends", currentUserId],
    queryFn: () => friendService.getFriends(currentUserId),
    enabled: !!currentUserId,
  });

  // 2. Fetch Temp Friends (shared groups with debt)
  const { tempFriends, isLoading: isLoadingTemp } = useTempFriends();

  // 3. Search Results
  const { data: searchResults, isLoading: isSearching } = useQuery({
    queryKey: ["users", "search", searchQuery],
    queryFn: async () => {
      if (searchQuery.length <= 2) return null;
      const response = await api.get<PaginatedResponse<User>>(
        `/users/search?query=${searchQuery}`,
      );
      return response.data;
    },
    enabled: searchQuery.length > 2,
  });

  const allDisplayUsers = useMemo(() => {
    const usersMap = new Map<number, DisplayUser>();

    // Add friends first
    (friends || []).forEach((f) => {
      if (f.id === currentUserId || excludeIds.includes(f.id)) return;
      usersMap.set(f.id, {
        id: f.id,
        firstName: f.firstName,
        lastName: f.lastName,
        username: f.username,
        type: "friend",
      });
    });

    // Add temp friends
    (tempFriends || []).forEach((tf) => {
      if (tf.userId === currentUserId || excludeIds.includes(tf.userId)) return;
      if (!usersMap.has(tf.userId)) {
        usersMap.set(tf.userId, {
          id: tf.userId,
          firstName: tf.firstName,
          lastName: tf.lastName,
          username: tf.username,
          type: "temp-friend",
        });
      }
    });

    // Add search results
    (searchResults?.content || []).forEach((u) => {
      if (u.id === currentUserId || excludeIds.includes(u.id)) return;
      if (!usersMap.has(u.id)) {
        usersMap.set(u.id, {
          id: u.id,
          firstName: u.firstName,
          lastName: u.lastName,
          username: u.username,
          type: "search-result",
        });
      }
    });

    // Convert map to array
    let users = Array.from(usersMap.values());

    if (searchQuery) {
      users = users.filter((u) => {
        const full = `${u.firstName} ${u.lastName} ${u.username}`.toLowerCase();
        return full.includes(searchQuery.toLowerCase());
      });
    }

    return users;
  }, [friends, tempFriends, searchResults, currentUserId, excludeIds, searchQuery]);

  const isLoading = isLoadingFriends || isLoadingTemp || (searchQuery.length > 2 && isSearching);

  return (
    <div className="space-y-4">
      <div className="relative">
        <Search
          className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"
          size={18}
        />
        <input
          type="text"
          placeholder={placeholder}
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all text-sm"
        />
      </div>

      <div className="max-h-48 max-h-60 overflow-y-auto border border-gray-200 rounded-lg p-2 space-y-1">
        {isLoading ? (
          <div className="flex justify-center py-8">
            <Loader2 className="animate-spin text-blue-600" size={24} />
          </div>
        ) : allDisplayUsers.length > 0 ? (
          allDisplayUsers.map((user) => (
            <div
              key={user.id}
              onClick={() => onToggle(user.id)}
              className={`flex items-center justify-between p-2 rounded-md cursor-pointer transition-colors ${
                selectedIds.includes(user.id)
                  ? "bg-blue-50 border-blue-100 border"
                  : "hover:bg-gray-50"
              }`}
            >
              <div className="flex items-center gap-2 overflow-hidden">
                <div className="w-8 h-8 flex-shrink-0 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 text-xs font-bold">
                  {user.firstName[0]}
                  {user.lastName ? user.lastName[0] : ""}
                </div>
                <div className="flex flex-col min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-gray-900 truncate">
                      {user.firstName} {user.lastName}
                    </span>
                    {user.type === "temp-friend" && (
                      <Badge variant="temp" className="text-[10px] py-0 px-1">
                        Temp Friend
                      </Badge>
                    )}
                    {user.type === "search-result" && (
                      <Badge variant="temp" className="text-[10px] py-0 px-1">
                        Global
                      </Badge>
                    )}
                  </div>
                  <span className="text-xs text-gray-500 truncate">
                    @{user.username}
                  </span>
                </div>
              </div>
              {selectedIds.includes(user.id) ? (
                <CheckCircle2
                  size={18}
                  className="text-blue-600 flex-shrink-0"
                />
              ) : (
                <UserPlus
                  size={18}
                  className="text-gray-400 flex-shrink-0"
                />
              )}
            </div>
          ))
        ) : (
          <div className="text-center py-8">
            <p className="text-sm text-gray-500">
              {searchQuery
                ? `No users found for "${searchQuery}"`
                : "Search to find people to add."}
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default MemberPicker;
