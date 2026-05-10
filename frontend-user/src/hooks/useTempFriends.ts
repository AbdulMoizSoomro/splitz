import { useQuery } from "@tanstack/react-query";
import { friendService } from "../features/users/friendService";
import { groupService } from "../features/groups/groupService";
import { useAuthStore } from "../store/authStore";

export interface TempFriend {
  userId: number;
  username: string;
  firstName: string;
  lastName: string;
  balance: number;
  groups: { id: number; name: string }[];
}

export const useTempFriends = () => {
  const user = useAuthStore((state) => state.user);
  const currentUserId = Number(user?.id);

  // 1. Fetch friends
  const { data: friends, isLoading: isLoadingFriends } = useQuery({
    queryKey: ["friends", currentUserId],
    queryFn: () => friendService.getFriends(currentUserId),
    enabled: !!currentUserId,
  });

  // 2. Fetch user balances (global)
  const { data: userBalances, isLoading: isLoadingUserBalances } = useQuery({
    queryKey: ["user-balances", currentUserId],
    queryFn: () => groupService.getUserBalances(currentUserId),
    enabled: !!currentUserId,
  });

  // 3. For each group with balance, fetch group balances (debts)
  const groupIds =
    userBalances?.groupBalances
      ?.map((gb) => gb.groupId)
      .sort((a, b) => a - b) || [];

  const { data: allGroupDebts, isLoading: isLoadingDebts } = useQuery({
    queryKey: ["temp-friends-debts", currentUserId, groupIds],
    queryFn: async () => {
      if (!userBalances || !userBalances.groupBalances) return [];

      const debtPromises = userBalances.groupBalances
        .filter((gb) => Math.abs(gb.balance) > 0.01)
        .map(async (gb) => {
          const balances = await groupService.getBalances(gb.groupId);
          return {
            groupId: gb.groupId,
            groupName: gb.groupName,
            simplifiedDebts: balances.simplifiedDebts,
          };
        });

      return await Promise.all(debtPromises);
    },
    enabled:
      !!userBalances &&
      !!userBalances.groupBalances &&
      userBalances.groupBalances.length > 0,
  });

  // Aggregate mutual balances with other users
  const mutualBalances: Record<number, TempFriend> = {};

  allGroupDebts?.forEach((groupDebt) => {
    groupDebt.simplifiedDebts.forEach((debt) => {
      if (debt.from === currentUserId || debt.to === currentUserId) {
        const otherId = debt.from === currentUserId ? debt.to : debt.from;
        const otherUsername =
          (debt.from === currentUserId ? debt.toUsername : debt.fromUsername) ||
          `User ${otherId}`;

        if (!mutualBalances[otherId]) {
          mutualBalances[otherId] = {
            userId: otherId,
            username: otherUsername,
            firstName: otherUsername.split(" ")[0],
            lastName: otherUsername.split(" ").slice(1).join(" "),
            balance: 0,
            groups: [],
          };
        }

        // Add group if not already added
        if (
          !mutualBalances[otherId].groups.some(
            (g) => g.id === groupDebt.groupId,
          )
        ) {
          mutualBalances[otherId].groups.push({
            id: groupDebt.groupId,
            name: groupDebt.groupName,
          });
        }

        if (debt.from === currentUserId) {
          mutualBalances[otherId].balance -= debt.amount;
        } else {
          mutualBalances[otherId].balance += debt.amount;
        }
      }
    });
  });

  const friendIds = new Set(friends?.map((f) => f.id));
  const tempFriends: TempFriend[] = Object.values(mutualBalances).filter(
    (mb) =>
      mb.userId !== currentUserId &&
      !friendIds.has(mb.userId) &&
      Math.abs(mb.balance) > 0.01,
  );

  const isLoading =
    isLoadingFriends ||
    isLoadingUserBalances ||
    ((userBalances?.groupBalances?.length ?? 0) > 0 && isLoadingDebts);

  return {
    tempFriends,
    isLoading,
  };
};
