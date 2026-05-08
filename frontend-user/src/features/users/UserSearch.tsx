import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Search, Loader2, UserPlus, Check, Clock } from 'lucide-react';
import api from '../../lib/axios';
import type { User, PaginatedResponse, Friendship } from '../../types/user';
import Input from '../../components/core/Input/Input';
import Button from '../../components/core/Button/Button';
import { useAuthStore } from '../../store/authStore';

const UserSearch = () => {
  const [query, setQuery] = useState('');
  const currentUser = useAuthStore((state) => state.user);
  const queryClient = useQueryClient();

  // Fetch search results
  const { data: searchData, isLoading: isSearching } = useQuery({
    queryKey: ['users', 'search', query],
    queryFn: async () => {
      if (!query) return null;
      const response = await api.get<PaginatedResponse<User>>(`/users/search?query=${query}`);
      return response.data;
    },
    enabled: query.length > 2,
  });

  // Fetch current user's friends to show status
  const { data: friends } = useQuery({
    queryKey: ['friends', currentUser?.id],
    queryFn: async () => {
      if (!currentUser?.id) return [];
      const response = await api.get<User[]>(`/users/${currentUser.id}/friends`);
      return response.data;
    },
    enabled: !!currentUser?.id,
  });

  // Fetch pending requests (incoming) to show status
  const { data: incomingRequests } = useQuery({
    queryKey: ['friend-requests', currentUser?.id, 'INCOMING'],
    queryFn: async () => {
      if (!currentUser?.id) return [];
      const response = await api.get<Friendship[]>(
        `/users/${currentUser.id}/friends/requests?direction=INCOMING`
      );
      return response.data;
    },
    enabled: !!currentUser?.id,
  });

  // Fetch pending requests (outgoing) to show status
  const { data: outgoingRequests } = useQuery({
    queryKey: ['friend-requests', currentUser?.id, 'OUTGOING'],
    queryFn: async () => {
      if (!currentUser?.id) return [];
      const response = await api.get<Friendship[]>(
        `/users/${currentUser.id}/friends/requests?direction=OUTGOING`
      );
      return response.data;
    },
    enabled: !!currentUser?.id,
  });

  // Send friend request mutation
  const sendRequestMutation = useMutation({
    mutationFn: async (friendId: number) => {
      if (!currentUser?.id) return;
      const response = await api.post<Friendship>(
        `/users/${currentUser.id}/friends?friendId=${friendId}`
      );
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['friend-requests', currentUser?.id] });
    },
  });

  // Accept/Reject mutation for immediate action in search
  const respondMutation = useMutation({
    mutationFn: async ({ friendshipId, action }: { friendshipId: number; action: 'accept' | 'reject' }) => {
      if (!currentUser?.id) return;
      const response = await api.put<Friendship>(
        `/users/${currentUser.id}/friends/${friendshipId}/${action}`
      );
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['friend-requests', currentUser?.id] });
      queryClient.invalidateQueries({ queryKey: ['friends', currentUser?.id] });
    },
  });

  const getFriendshipStatus = (userId: number) => {
    if (userId.toString() === currentUser?.id) return { type: 'self' };
    
    const isFriend = friends?.some((f) => f.id === userId);
    if (isFriend) return { type: 'friend' };

    const incoming = incomingRequests?.find((r) => r.requesterId === userId);
    if (incoming) return { type: 'incoming', id: incoming.id };

    const hasOutgoing = outgoingRequests?.some((r) => r.addresseeId === userId);
    if (hasOutgoing) return { type: 'outgoing' };

    return { type: 'none' };
  };

  return (
    <div className="w-full space-y-4">
      <div className="relative">
        <Input
          placeholder="Search by name or email..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="pl-10"
        />
        <Search className="absolute left-3 top-2.5 text-gray-400" size={20} />
      </div>

      <div className="space-y-2">
        {isSearching && (
          <div className="flex justify-center py-4">
            <Loader2 className="animate-spin text-blue-600" size={24} />
          </div>
        )}

        {!isSearching && query.length > 2 && searchData?.content.length === 0 && (
          <p className="text-center text-gray-500 py-4">No users found.</p>
        )}

        {searchData?.content.map((user) => {
          const status = getFriendshipStatus(user.id);
          const isSending = sendRequestMutation.isPending && sendRequestMutation.variables === user.id;
          const isResponding = respondMutation.isPending && respondMutation.variables?.friendshipId === status.id;
          
          return (
            <div
              key={user.id}
              className="flex items-center justify-between p-4 bg-white border border-gray-200 rounded-lg shadow-sm"
            >
              <div>
                <p className="font-semibold text-gray-900">
                  {user.firstName} {user.lastName}
                </p>
                <p className="text-sm text-gray-500">@{user.username}</p>
              </div>

              <div>
                {status.type === 'self' && (
                  <span className="text-xs font-medium text-gray-400 uppercase tracking-wider">
                    You
                  </span>
                )}
                {status.type === 'friend' && (
                  <div className="flex items-center gap-1 text-green-600">
                    <Check size={16} />
                    <span className="text-sm font-medium">Friends</span>
                  </div>
                )}
                {status.type === 'outgoing' && (
                  <div className="flex items-center gap-1 text-orange-500">
                    <Clock size={16} />
                    <span className="text-sm font-medium">Pending</span>
                  </div>
                )}
                {status.type === 'incoming' && (
                  <div className="flex items-center gap-2">
                    <Button
                      size="sm"
                      variant="primary"
                      onClick={() => respondMutation.mutate({ friendshipId: status.id!, action: 'accept' })}
                      disabled={isResponding}
                    >
                      {isResponding && respondMutation.variables?.action === 'accept' ? (
                        <Loader2 className="animate-spin" size={16} />
                      ) : (
                        'Accept'
                      )}
                    </Button>
                    <Button
                      size="sm"
                      variant="danger"
                      onClick={() => respondMutation.mutate({ friendshipId: status.id!, action: 'reject' })}
                      disabled={isResponding}
                    >
                      {isResponding && respondMutation.variables?.action === 'reject' ? (
                        <Loader2 className="animate-spin" size={16} />
                      ) : (
                        'Reject'
                      )}
                    </Button>
                  </div>
                )}
                {status.type === 'none' && (
                  <Button
                    size="sm"
                    variant="secondary"
                    className="flex items-center gap-1"
                    onClick={() => sendRequestMutation.mutate(user.id)}
                    disabled={isSending}
                  >
                    {isSending ? (
                      <Loader2 className="animate-spin" size={16} />
                    ) : (
                      <UserPlus size={16} />
                    )}
                    <span>{isSending ? 'Sending...' : 'Add Friend'}</span>
                  </Button>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default UserSearch;
