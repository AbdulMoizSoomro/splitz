import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { UserPlus, Loader2, AlertCircle } from 'lucide-react';
import { friendService } from './friendService';
import { groupService } from '../groups/groupService';
import { useAuthStore } from '../../store/authStore';
import Button from '../../components/core/Button/Button';
import { Card, CardHeader, CardTitle, CardContent } from '../../components/core/Card/Card';

const TempFriendsList = () => {
  const user = useAuthStore((state) => state.user);
  const currentUserId = Number(user?.id);
  const queryClient = useQueryClient();

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

  // Add friend mutation
  const addFriendMutation = useMutation({
    mutationFn: (friendId: number) => friendService.sendFriendRequest(currentUserId, friendId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['friend-requests', currentUserId] });
    },
  });

  if (isLoadingFriends || isLoadingUserBalances || ((userBalances?.groupBalances?.length ?? 0) > 0 && isLoadingDebts)) {
    return (
      <div className="flex justify-center py-4">
        <Loader2 className="animate-spin text-blue-600" size={24} />
      </div>
    );
  }

  // Aggregate mutual balances with other users
  const mutualBalances: Record<number, { userId: number, username: string, balance: number }> = {};
  
  allGroupDebts?.forEach((debt) => {
    if (debt.from === currentUserId) {
      // Current user owes someone
      const otherId = debt.to;
      const otherUsername = debt.toUsername || `User ${otherId}`;
      if (!mutualBalances[otherId]) {
        mutualBalances[otherId] = { userId: otherId, username: otherUsername, balance: 0 };
      }
      mutualBalances[otherId].balance -= debt.amount;
    } else if (debt.to === currentUserId) {
      // Someone owes current user
      const otherId = debt.from;
      const otherUsername = debt.fromUsername || `User ${otherId}`;
      if (!mutualBalances[otherId]) {
        mutualBalances[otherId] = { userId: otherId, username: otherUsername, balance: 0 };
      }
      mutualBalances[otherId].balance += debt.amount;
    }
  });

  // Filter out friends and self, and zero balances
  const friendIds = new Set(friends?.map(f => f.id));
  const tempFriends = Object.values(mutualBalances)
    .filter(mb => mb.userId !== currentUserId && !friendIds.has(mb.userId) && Math.abs(mb.balance) > 0.01);

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
          {tempFriends.map((tf) => (
            <div key={tf.userId} className="flex items-center justify-between p-3 bg-white border border-orange-100 rounded-lg shadow-sm">
              <div>
                <p className="font-semibold text-gray-900">{tf.username}</p>
                <p className={`text-sm ${tf.balance > 0 ? 'text-green-600' : 'text-red-600'}`}>
                  {tf.balance > 0 ? `Owes you ${tf.balance.toFixed(2)}` : `You owe ${Math.abs(tf.balance).toFixed(2)}`}
                </p>
              </div>
              <Button
                size="sm"
                variant="secondary"
                className="flex items-center gap-1 border-orange-200 hover:bg-orange-50"
                onClick={() => addFriendMutation.mutate(tf.userId)}
                disabled={addFriendMutation.isPending}
              >
                {addFriendMutation.isPending ? (
                  <Loader2 className="animate-spin" size={16} />
                ) : (
                  <UserPlus size={16} />
                )}
                <span>{addFriendMutation.isPending ? 'Sending...' : 'Add Friend'}</span>
              </Button>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
};

export default TempFriendsList;
