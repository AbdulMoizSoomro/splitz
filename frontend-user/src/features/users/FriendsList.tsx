import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Users, Loader2, UserMinus } from 'lucide-react';
import api from '../../lib/axios';
import type { User } from '../../types/user';
import { useAuthStore } from '../../store/authStore';
import Button from '../../components/core/Button/Button';

const FriendsList = () => {
  const currentUser = useAuthStore((state) => state.user);
  const queryClient = useQueryClient();

  const { data: friends, isLoading } = useQuery({
    queryKey: ['friends', currentUser?.id],
    queryFn: async () => {
      if (!currentUser?.id) return [];
      const response = await api.get<User[]>(`/users/${currentUser.id}/friends`);
      return response.data;
    },
    enabled: !!currentUser?.id,
  });

  const removeFriendMutation = useMutation({
    mutationFn: async (friendId: number) => {
      if (!currentUser?.id) return;
      await api.delete(`/users/${currentUser.id}/friends/${friendId}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['friends', currentUser?.id] });
    },
  });

  if (isLoading) {
    return (
      <div className="flex justify-center py-4">
        <Loader2 className="animate-spin text-blue-600" size={24} />
      </div>
    );
  }

  if (!friends || friends.length === 0) {
    return (
      <div className="text-center py-6 bg-gray-50 border border-dashed border-gray-300 rounded-lg">
        <Users className="mx-auto text-gray-400 mb-2" size={32} />
        <p className="text-sm text-gray-500">No friends added yet.</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {friends.map((friend) => {
        const isRemoving = removeFriendMutation.isPending && removeFriendMutation.variables === friend.id;
        
        return (
          <div
            key={friend.id}
            className="flex items-center justify-between p-3 bg-white border border-gray-200 rounded-lg shadow-sm"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 font-bold">
                {friend.firstName[0]}
                {friend.lastName ? friend.lastName[0] : ''}
              </div>
              <div>
                <p className="text-sm font-semibold text-gray-900">
                  {friend.firstName} {friend.lastName}
                </p>
                <p className="text-xs text-gray-500">@{friend.username}</p>
              </div>
            </div>

            <Button
              size="sm"
              variant="secondary"
              className="text-gray-400 hover:text-red-600 hover:bg-red-50"
              onClick={() => {
                if (window.confirm(`Are you sure you want to remove ${friend.firstName} from your friends?`)) {
                  removeFriendMutation.mutate(friend.id);
                }
              }}
              disabled={isRemoving}
              title="Remove Friend"
            >
              {isRemoving ? (
                <Loader2 className="animate-spin" size={16} />
              ) : (
                <UserMinus size={16} />
              )}
            </Button>
          </div>
        );
      })}
    </div>
  );
};

export default FriendsList;
