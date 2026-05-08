import { useQuery } from '@tanstack/react-query';
import { friendService } from '../features/users/friendService';
import { groupService } from '../features/groups/groupService';
import { useAuthStore } from '../store/authStore';

export interface TempFriend {
  userId: number;
  username: string;
  firstName: string;
  lastName: string;
  balance: number;
}

export const useTempFriends = () => {
  const user = useAuthStore((state) => state.user);
  const currentUserId = Number(user?.id);

  // 1. Fetch friends
  const { data: friends, isLoading: isLoadingFriends } = useQuery({
    queryKey: ['friends', currentUserId],
    queryFn: () => friendService.getFriends(currentUserId),
    enabled: !!currentUserId,
  });

  // 2. Fetch user balances (global)
  const { data: userBalances, isLoading: isLoadingUserBalances } = useQuery({
    queryKey: ['user-balances', currentUserId],
    queryFn: () => groupService.getUserBalances(currentUserId),
    enabled: !!currentUserId,
  });

  // 3. For each group with balance, fetch group balances (debts)
  const { data: allGroupDebts, isLoading: isLoadingDebts } = useQuery({
    queryKey: ['temp-friends-debts', currentUserId, userBalances?.groupBalances?.length],
    queryFn: async () => {
      if (!userBalances || !userBalances.groupBalances) return [];
      
      const debtPromises = userBalances.groupBalances
        .filter((gb) => Math.abs(gb.balance) > 0.01)
        .map((gb) => groupService.getBalances(gb.groupId));
      
      const results = await Promise.all(debtPromises);
      return results.flatMap(r => r.simplifiedDebts);
    },
    enabled: !!userBalances && !!userBalances.groupBalances && userBalances.groupBalances.length > 0,
  });

  // Aggregate mutual balances with other users
  const mutualBalances: Record<number, { userId: number, username: string, firstName: string, lastName: string, balance: number }> = {};
  
  allGroupDebts?.forEach((debt) => {
    if (debt.from === currentUserId) {
      // Current user owes someone
      const otherId = debt.to;
      const otherUsername = debt.toUsername || `User ${otherId}`;
      if (!mutualBalances[otherId]) {
        mutualBalances[otherId] = { 
          userId: otherId, 
          username: otherUsername, 
          firstName: otherUsername.split(' ')[0], 
          lastName: otherUsername.split(' ').slice(1).join(' '),
          balance: 0 
        };
      }
      mutualBalances[otherId].balance -= debt.amount;
    } else if (debt.to === currentUserId) {
      // Someone owes current user
      const otherId = debt.from;
      const otherUsername = debt.fromUsername || `User ${otherId}`;
      if (!mutualBalances[otherId]) {
        mutualBalances[otherId] = { 
          userId: otherId, 
          username: otherUsername, 
          firstName: otherUsername.split(' ')[0], 
          lastName: otherUsername.split(' ').slice(1).join(' '),
          balance: 0 
        };
      }
      mutualBalances[otherId].balance += debt.amount;
    }
  });

  const friendIds = new Set(friends?.map(f => f.id));
  const tempFriends: TempFriend[] = Object.values(mutualBalances)
    .filter(mb => mb.userId !== currentUserId && !friendIds.has(mb.userId) && Math.abs(mb.balance) > 0.01);

  const isLoading = isLoadingFriends || isLoadingUserBalances || ((userBalances?.groupBalances?.length ?? 0) > 0 && isLoadingDebts);

  return {
    tempFriends,
    isLoading
  };
};
