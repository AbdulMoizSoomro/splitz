import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import api from "../../lib/axios";
import { useAuthStore } from "../../store/authStore";
import { friendService } from "./friendService";
import { groupService } from "../groups/groupService";
import { expenseService } from "../expenses/expenseService";
import { queryKeys, invalidations, bindInvalidations } from "../../lib/queryKeys";
import { MONEY_TOLERANCE } from "../balances/ledger";
import { mergeActivity, type ActivityItem } from "./unifiedActivity";
import type { User, Friendship } from "../../types/user";

export type RelationshipStatus =
  | "CONFIRMED_FRIEND"
  | "PENDING_OUTGOING"
  | "PENDING_INCOMING"
  | "TEMP_FRIEND"
  | "NONE";

export interface GroupBalanceItem {
  groupId: number;
  groupName: string;
  balance: number;
}

export interface DeriveRelationshipOptions {
  targetUserId: number;
  friends?: User[];
  outgoingRequests?: Friendship[];
  incomingRequests?: Friendship[];
  netBalance?: number;
}

/**
 * Derive the exact relationship status between current user and target user.
 */
export function deriveRelationshipStatus({
  targetUserId,
  friends = [],
  outgoingRequests = [],
  incomingRequests = [],
  netBalance = 0,
}: DeriveRelationshipOptions): RelationshipStatus {
  const safeFriends = Array.isArray(friends) ? friends : [];
  const safeOutgoing = Array.isArray(outgoingRequests) ? outgoingRequests : [];
  const safeIncoming = Array.isArray(incomingRequests) ? incomingRequests : [];

  const isFriend = safeFriends.some((f) => f.id === targetUserId);
  if (isFriend) return "CONFIRMED_FRIEND";

  if (Math.abs(netBalance) > MONEY_TOLERANCE) {
    return "TEMP_FRIEND";
  }

  const isOutgoing = safeOutgoing.some(
    (r) => r.addresseeId === targetUserId || r.friendId === targetUserId,
  );
  if (isOutgoing) return "PENDING_OUTGOING";

  const isIncoming = safeIncoming.some(
    (r) => r.requesterId === targetUserId || r.friendId === targetUserId,
  );
  if (isIncoming) return "PENDING_INCOMING";

  return "NONE";
}

export interface AutoAllocateOptions {
  amount: number;
  type: "PAY" | "RECEIVE";
  groupBalances: GroupBalanceItem[];
}

/**
 * Automatically allocates a settlement amount across shared group debts.
 */
export function autoAllocateSettlement({
  amount,
  type,
  groupBalances,
}: AutoAllocateOptions): Record<number, number> {
  const allocations: Record<number, number> = {};
  let remaining = amount;

  for (const group of groupBalances) {
    const relevantBalance = Math.abs(group.balance);
    const isOwed =
      (type === "PAY" && group.balance < 0) ||
      (type === "RECEIVE" && group.balance > 0);

    if (isOwed && relevantBalance > 0 && remaining > 0) {
      const allocateAmount = Math.min(relevantBalance, remaining);
      allocations[group.groupId] = allocateAmount;
      remaining -= allocateAmount;
    }
  }

  return allocations;
}

/** Pure helper to parse allocation values consistently. */
export function parseAllocationValue(val: string | number): number {
  return typeof val === "number" ? val : parseFloat(val) || 0;
}

export interface ValidateAllocationOptions {
  amount: number;
  isAllocating: boolean;
  allocations: Record<number, string | number>;
}

export interface AllocationValidationResult {
  isValid: boolean;
  totalAllocated: number;
  difference: number;
}

/**
 * Validate that manual allocations sum up to the total settlement amount.
 */
export function validateSettlementAllocation({
  amount,
  isAllocating,
  allocations,
}: ValidateAllocationOptions): AllocationValidationResult {
  if (!isAllocating) {
    return { isValid: true, totalAllocated: 0, difference: 0 };
  }

  const totalAllocated = Object.values(allocations).reduce(
    (sum, val) => sum + parseAllocationValue(val),
    0,
  );

  const difference = totalAllocated - amount;
  const isValid = Math.abs(difference) < 0.01;

  return { isValid, totalAllocated, difference };
}

export interface BuildSettlementPayloadOptions {
  currentUserId: number;
  friendId: number;
  type: "PAY" | "RECEIVE";
  amount: number;
  isAllocating: boolean;
  allocations: Record<number, string | number>;
}

/**
 * Construct the payload DTO for creating a settlement.
 */
export function buildSettlementPayload({
  currentUserId,
  friendId,
  type,
  amount,
  isAllocating,
  allocations,
}: BuildSettlementPayloadOptions) {
  const payerId = type === "PAY" ? currentUserId : friendId;
  const payeeId = type === "PAY" ? friendId : currentUserId;

  const payload: {
    payerId: number;
    payeeId: number;
    amount: number;
    allocations?: { groupId: number; amount: number }[];
  } = {
    payerId,
    payeeId,
    amount,
  };

  if (isAllocating) {
    payload.allocations = Object.entries(allocations)
      .map(([groupId, val]) => ({
        groupId: parseInt(groupId),
        amount: parseAllocationValue(val),
      }))
      .filter((a) => a.amount > 0);
  }

  return payload;
}

/**
 * Deep React Hook Seam for Interpersonal operations and state for a target friend.
 */
export function useInterpersonalFriend(friendId: number) {
  const queryClient = useQueryClient();
  const invalidate = bindInvalidations(queryClient);
  const authState = useAuthStore();
  const user = (authState as any)?.user ?? authState;
  const currentUserId = Number(user?.id);
  const enabled = !isNaN(currentUserId) && currentUserId > 0 && !!friendId;

  // 1. Friend user detail
  const friendQuery = useQuery({
    queryKey: queryKeys.user(friendId),
    queryFn: async () => {
      const response = await api.get<User>(`/users/${friendId}`);
      return response.data;
    },
    enabled,
  });

  // 2. Net balance and shared group balance breakdown
  const balanceQuery = useQuery({
    queryKey: queryKeys.friendBalance(currentUserId, friendId),
    queryFn: () => friendService.getNetBalance(currentUserId, friendId),
    enabled,
  });

  // 3. Shared groups
  const groupsQuery = useQuery({
    queryKey: queryKeys.groups(),
    queryFn: () => groupService.getGroups(),
  });

  const sharedGroups = (groupsQuery.data ?? []).filter((g) =>
    g.members.some((m) => m.userId === friendId),
  );

  const sharedGroupIds = sharedGroups.map((g) => g.id);

  // 4. Shared expenses
  const sharedExpensesQuery = useQuery({
    queryKey: queryKeys.sharedExpenses(friendId, sharedGroupIds),
    queryFn: async () => {
      const allExpenses = await expenseService.getBulkGroupExpenses(sharedGroupIds);
      return allExpenses.filter(
        (e) => e.paidBy === friendId || e.splits.some((s) => s.userId === friendId),
      );
    },
    enabled: enabled && sharedGroupIds.length > 0,
  });

  // 5. Settlements with friend
  const settlementsQuery = useQuery({
    queryKey: queryKeys.friendSettlements(currentUserId, friendId),
    queryFn: () => friendService.getSettlementsWithFriend(currentUserId, friendId),
    enabled,
  });

  // 6. Friends list & Request edges for relationship classification
  const friendsQuery = useQuery({
    queryKey: queryKeys.friends(currentUserId),
    queryFn: () => friendService.getFriends(currentUserId),
    enabled,
  });

  const outgoingRequestsQuery = useQuery({
    queryKey: queryKeys.friendRequests(currentUserId, "OUTGOING"),
    queryFn: () => friendService.getFriendRequests(currentUserId, "OUTGOING"),
    enabled,
  });

  const incomingRequestsQuery = useQuery({
    queryKey: queryKeys.friendRequests(currentUserId, "INCOMING"),
    queryFn: () => friendService.getFriendRequests(currentUserId, "INCOMING"),
    enabled,
  });

  const relationshipStatus = deriveRelationshipStatus({
    targetUserId: friendId,
    friends: friendsQuery.data,
    outgoingRequests: outgoingRequestsQuery.data,
    incomingRequests: incomingRequestsQuery.data,
    netBalance: balanceQuery.data?.netBalance ?? 0,
  });

  // Unified activity feed
  const activityFeed: ActivityItem[] = mergeActivity(
    sharedExpensesQuery.data,
    settlementsQuery.data,
  );

  // Mutations
  const sendFriendRequestMutation = useMutation({
    mutationFn: () => friendService.sendFriendRequest(currentUserId, friendId),
    onSuccess: () => {
      invalidations.friendshipMutated(invalidate, currentUserId);
    },
  });

  const cancelFriendRequestMutation = useMutation({
    mutationFn: () => friendService.removeFriend(currentUserId, friendId),
    onSuccess: () => {
      invalidations.friendshipMutated(invalidate, currentUserId);
    },
  });

  const createSettlementMutation = useMutation({
    mutationFn: (data: {
      payerId: number;
      payeeId: number;
      amount: number;
      allocations?: { groupId: number; amount: number }[];
    }) => friendService.createSettlement(data),
    onSuccess: () => {
      invalidations.settlementMutated(invalidate, currentUserId, friendId);
    },
  });

  const updateSettlementMutation = useMutation({
    mutationFn: ({ settlementId, amount }: { settlementId: number; amount: number }) =>
      friendService.updateSettlement(settlementId, { amount }),
    onSuccess: () => {
      invalidations.settlementMutated(invalidate, currentUserId, friendId);
    },
  });

  const confirmSettlementMutation = useMutation({
    mutationFn: (settlementId: number) =>
      friendService.confirmSettlement(settlementId),
    onSuccess: () => {
      invalidations.settlementMutated(invalidate, currentUserId, friendId);
    },
  });

  return {
    currentUserId,
    friend: friendQuery.data ?? undefined,
    relationshipStatus,
    netBalance: balanceQuery.data?.netBalance ?? 0,
    directBalance: balanceQuery.data?.directBalance ?? 0,
    groupBalances: balanceQuery.data?.groupBalances ?? [],
    sharedGroups,
    activityFeed,
    isLoading:
      friendQuery.isLoading ||
      balanceQuery.isLoading ||
      groupsQuery.isLoading ||
      settlementsQuery.isLoading,
    mutations: {
      sendFriendRequest: sendFriendRequestMutation,
      cancelFriendRequest: cancelFriendRequestMutation,
      createSettlement: createSettlementMutation,
      updateSettlement: updateSettlementMutation,
      confirmSettlement: confirmSettlementMutation,
    },
  };
}
