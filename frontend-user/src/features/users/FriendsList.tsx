import { useState, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Users, Loader2, Search, X, Filter, ChevronDown } from "lucide-react";
import api from "../../lib/axios";
import type { User, Friendship } from "../../types/user";
import { useAuthStore } from "../../store/authStore";
import { useLedger } from "../../hooks/useLedger";
import { friendService } from "./friendService";
import {
  deriveUnifiedConnections,
  computeFriendsSummary,
  filterAndSortConnections,
  type ConnectionFilter,
  type UnifiedConnection,
} from "./friendsLedger";
import { FriendsSummaryBanner } from "./components/FriendsSummaryBanner";
import { FriendConnectionCard } from "./components/FriendConnectionCard";
import FriendshipSettlementModal from "./FriendshipSettlementModal";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import { MONEY_TOLERANCE } from "../balances/ledger";

const FriendsList = () => {
  const currentUser = useAuthStore((state) => state.user);
  const currentUserId = Number(currentUser?.id);
  const queryClient = useQueryClient();

  const [filter, setFilter] = useState<ConnectionFilter>("ALL");
  const [search, setSearch] = useState("");
  const [settlingConnection, setSettlingConnection] =
    useState<UnifiedConnection | null>(null);

  // 1. Fetch ledger data (counterparties, friends list, and group balances in one place)
  const { ledger, friends, isLoading: isLedgerLoading } = useLedger({
    detail: true,
  });

  // 2. Fetch outgoing friend requests to check pending invites
  const { data: outgoingRequests, isLoading: isRequestsLoading } = useQuery({
    queryKey: ["friend-requests", currentUserId, "OUTGOING"],
    queryFn: () => {
      if (!currentUserId) return Promise.resolve<Friendship[]>([]);
      return friendService.getFriendRequests(currentUserId, "OUTGOING");
    },
    enabled: !!currentUserId,
  });

  // 3. Mutations
  const removeFriendMutation = useMutation({
    mutationFn: async (friendId: number) => {
      if (!currentUserId) return;
      await api.delete(`/users/${currentUserId}/friends/${friendId}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["friends", currentUserId] });
      queryClient.invalidateQueries({
        queryKey: ["counterparties", currentUserId],
      });
      queryClient.invalidateQueries({
        queryKey: ["user-balances", currentUserId],
      });
    },
  });

  const addFriendMutation = useMutation({
    mutationFn: async (friendId: number) => {
      if (!currentUserId) return;
      await friendService.sendFriendRequest(currentUserId, friendId);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["friend-requests", currentUserId],
      });
    },
  });

  const cancelRequestMutation = useMutation({
    mutationFn: async (friendId: number) => {
      if (!currentUserId) return;
      await friendService.removeFriend(currentUserId, friendId);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["friend-requests", currentUserId],
      });
    },
  });

  // 4. Derive unified connections and financial summary
  const allConnections = useMemo(() => {
    return deriveUnifiedConnections({
      friends: friends ?? [],
      counterparties: ledger?.counterparties ?? [],
      outgoingRequests: outgoingRequests ?? [],
    });
  }, [friends, ledger?.counterparties, outgoingRequests]);

  const summary = useMemo(() => {
    return computeFriendsSummary(allConnections);
  }, [allConnections]);

  const filteredConnections = useMemo(() => {
    return filterAndSortConnections(allConnections, { filter, search });
  }, [allConnections, filter, search]);

  const counts = useMemo(() => {
    let youOwe = 0;
    let owedToYou = 0;
    let settled = 0;

    for (const c of allConnections) {
      if (c.balance < -MONEY_TOLERANCE) youOwe++;
      else if (c.balance > MONEY_TOLERANCE) owedToYou++;
      else settled++;
    }

    return {
      all: allConnections.length,
      youOwe,
      owedToYou,
      settled,
    };
  }, [allConnections]);

  const isLoading = isLedgerLoading || isRequestsLoading;

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <Loader2 className="animate-spin text-blue-600" size={28} />
      </div>
    );
  }

  const currentUserModel: User | null = currentUser
    ? {
        id: currentUserId,
        username: currentUser.username || "",
        email: currentUser.email || "",
        firstName:
          (currentUser as any).firstName || currentUser.username || "User",
        lastName: (currentUser as any).lastName || "",
      }
    : null;

  const friendModel: User | null = settlingConnection
    ? {
        id: settlingConnection.userId,
        username: settlingConnection.username,
        email: settlingConnection.email || "",
        firstName:
          settlingConnection.firstName || settlingConnection.name || "User",
        lastName: settlingConnection.lastName || "",
      }
    : null;

  const filterOptions: Array<{
    id: ConnectionFilter;
    label: string;
    count: number;
  }> = [
    { id: "ALL", label: "All", count: counts.all },
    { id: "YOU_OWE", label: "You Owe", count: counts.youOwe },
    { id: "OWED_TO_YOU", label: "Owed to You", count: counts.owedToYou },
    { id: "SETTLED", label: "Settled", count: counts.settled },
  ];

  const activeFilterOption =
    filterOptions.find((o) => o.id === filter) || filterOptions[0];

  return (
    <div className="space-y-6">
      {/* 3-Metric Summary Banner */}
      <FriendsSummaryBanner summary={summary} />

      {/* Filter and In-List Search Controls */}
      <div className="space-y-2 pt-2">
        {/* Mobile: Filter dropdown button + Search input in one tidy row */}
        <div className="flex sm:hidden items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger
              className="h-9 px-2.5 border border-border rounded-lg bg-card hover:bg-muted text-xs font-medium flex items-center gap-1.5 shrink-0 shadow-xs transition-colors"
              aria-label="Filter connections"
            >
              <Filter size={14} className="text-muted-foreground" />
              <span>{activeFilterOption.label}</span>
              <span className="text-[10px] bg-muted px-1.5 py-0.5 rounded-full text-muted-foreground font-semibold">
                {activeFilterOption.count}
              </span>
              <ChevronDown size={12} className="text-muted-foreground" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="w-44">
              {filterOptions.map((opt) => (
                <DropdownMenuItem
                  key={opt.id}
                  onClick={() => setFilter(opt.id)}
                  className={`flex items-center justify-between text-xs cursor-pointer ${
                    filter === opt.id ? "font-semibold bg-muted" : ""
                  }`}
                >
                  <span>{opt.label}</span>
                  <span className="text-[10px] text-muted-foreground font-normal">
                    ({opt.count})
                  </span>
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>

          <div className="relative flex-1">
            <Search
              className="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground"
              size={15}
            />
            <Input
              placeholder="Search..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-8 pr-8 h-9 text-xs"
            />
            {search && (
              <button
                onClick={() => setSearch("")}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                <X size={14} />
              </button>
            )}
          </div>
        </div>

        {/* Desktop: Filter Pills + Search input */}
        <div className="hidden sm:flex items-center justify-between gap-3">
          <div className="flex items-center gap-1.5">
            {filterOptions.map((opt) => {
              const isActive = filter === opt.id;
              return (
                <button
                  key={opt.id}
                  onClick={() => setFilter(opt.id)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all flex items-center gap-1.5 whitespace-nowrap ${
                    isActive
                      ? "bg-primary text-primary-foreground shadow-xs"
                      : "bg-muted/70 text-muted-foreground hover:bg-muted hover:text-foreground"
                  }`}
                >
                  <span>{opt.label}</span>
                  <span
                    className={`text-[10px] px-1.5 py-0.2 rounded-full ${
                      isActive
                        ? "bg-primary-foreground/20 text-primary-foreground"
                        : "bg-background text-muted-foreground"
                    }`}
                  >
                    {opt.count}
                  </span>
                </button>
              );
            })}
          </div>

          <div className="relative flex-1 max-w-xs">
            <Search
              className="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground"
              size={16}
            />
            <Input
              placeholder="Search connections or groups..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-8 pr-8 h-9 text-xs"
            />
            {search && (
              <button
                onClick={() => setSearch("")}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                <X size={14} />
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Connections List */}
      {filteredConnections.length === 0 ? (
        <Card className="text-center py-10 bg-muted/40 border border-dashed border-border shadow-none">
          <CardContent className="pt-6 space-y-2">
            <Users className="mx-auto text-muted-foreground mb-2" size={36} />
            <p className="text-sm font-medium text-foreground">
              {search
                ? `No connections matching "${search}"`
                : filter === "YOU_OWE"
                  ? "You don't owe anyone right now! 🎉"
                  : filter === "OWED_TO_YOU"
                    ? "No one owes you right now."
                    : filter === "SETTLED"
                      ? "No settled connections yet."
                      : "No friends added yet."}
            </p>
            <p className="text-xs text-muted-foreground max-w-sm mx-auto">
              {search
                ? "Try adjusting your search terms or filter to see more connections."
                : "Search for friends or join shared groups to start tracking shared expenses."}
            </p>
            {search && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setSearch("")}
                className="mt-2 text-xs"
              >
                Clear Search
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {filteredConnections.map((connection) => (
            <FriendConnectionCard
              key={connection.userId}
              connection={connection}
              onSettleUp={(conn) => setSettlingConnection(conn)}
              onAddFriend={(conn) => addFriendMutation.mutate(conn.userId)}
              onRemoveFriend={(conn) => {
                if (
                  window.confirm(
                    `Are you sure you want to remove ${conn.name} from your friends?`,
                  )
                ) {
                  removeFriendMutation.mutate(conn.userId);
                }
              }}
              onCancelRequest={(conn) =>
                cancelRequestMutation.mutate(conn.userId)
              }
              isAddFriendPending={
                addFriendMutation.isPending &&
                addFriendMutation.variables === connection.userId
              }
              isRemoveFriendPending={
                removeFriendMutation.isPending &&
                removeFriendMutation.variables === connection.userId
              }
            />
          ))}
        </div>
      )}

      {/* Settlement Modal */}
      {settlingConnection && currentUserModel && friendModel && (
        <FriendshipSettlementModal
          isOpen={!!settlingConnection}
          onClose={() => setSettlingConnection(null)}
          currentUser={currentUserModel}
          friend={friendModel}
          suggestedAmount={settlingConnection.balance}
        />
      )}
    </div>
  );
};

export default FriendsList;
