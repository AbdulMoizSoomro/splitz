import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Loader2, UserPlus, CheckCircle2, Search, ExternalLink } from 'lucide-react';
import { Link } from 'react-router-dom';
import Modal from '../../components/core/Modal/Modal';
import Button from '../../components/core/Button/Button';
import Badge from '../../components/core/Badge/Badge';
import { groupService } from './groupService';
import api from '../../lib/axios';
import { useAuthStore } from '../../store/authStore';
import { useToastStore } from '../../store/toastStore';
import { useTempFriends } from '../../hooks/useTempFriends';
import type { User } from '../../types/user';
import type { Group } from '../../types/group';

interface AddMemberModalProps {
  isOpen: boolean;
  onClose: () => void;
  group: Group;
}

interface EligibleUser {
  id: number;
  firstName: string;
  lastName?: string;
  username: string;
  isTemp: boolean;
}

const AddMemberModal = ({ isOpen, onClose, group }: AddMemberModalProps) => {
  const [selectedUserIds, setSelectedUserIds] = useState<number[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const queryClient = useQueryClient();
  const currentUser = useAuthStore((state) => state.user);
  const { addToast } = useToastStore();

  const { data: friends, isLoading: isLoadingFriends } = useQuery({
    queryKey: ['friends', currentUser?.id],
    queryFn: async () => {
      if (!currentUser?.id) return [];
      const response = await api.get<User[]>(`/users/${currentUser.id}/friends`);
      return response.data;
    },
    enabled: isOpen && !!currentUser?.id,
  });

  const { tempFriends, isLoading: isLoadingTemp } = useTempFriends();

  // combine friends and temp friends
  const allEligible = React.useMemo(() => {
    const existingMemberIds = new Set(group.members.map((m) => m.userId));

    const friendList: EligibleUser[] = (friends ?? [])
      .filter((f) => !existingMemberIds.has(f.id))
      .map((f) => ({
        id: f.id,
        firstName: f.firstName,
        lastName: f.lastName,
        username: f.username,
        isTemp: false,
      }));

    const tempFriendList: EligibleUser[] = (tempFriends ?? [])
      .filter((tf) => !existingMemberIds.has(tf.userId))
      .map((tf) => ({
        id: tf.userId,
        firstName: tf.firstName,
        lastName: tf.lastName,
        username: tf.username,
        isTemp: true,
      }));

    // Dedup
    const combined = [...friendList];
    const seen = new Set(combined.map((u) => u.id));

    tempFriendList.forEach((tf) => {
      if (!seen.has(tf.id)) {
        combined.push(tf);
        seen.add(tf.id);
      }
    });

    return combined;
  }, [friends, tempFriends, group.members]);

  const filteredUsers = allEligible.filter(u => {
    const full = `${u.firstName} ${u.lastName} ${u.username}`.toLowerCase();
    return full.includes(searchQuery.toLowerCase());
  });

  const bulkAddMutation = useMutation({
    mutationFn: () => groupService.bulkAddMembers(group.id, selectedUserIds),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', String(group.id)] });
      addToast('Members added successfully', 'success');
      setSelectedUserIds([]);
      setSearchQuery('');
      onClose();
    },
    onError: () => {
      addToast('Failed to add members', 'error');
    },
  });

  const toggleUser = (id: number) => {
    setSelectedUserIds((prev) =>
      prev.includes(id) ? prev.filter((uid) => uid !== id) : [...prev, id],
    );
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedUserIds.length === 0) return;
    bulkAddMutation.mutate();
  };

  const handleClose = () => {
    setSelectedUserIds([]);
    setSearchQuery('');
    onClose();
  };

  const isLoading = isLoadingFriends || isLoadingTemp;

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title="Add Members">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <div className="relative mb-3">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={18} />
            <input
              type="text"
              placeholder="Search friends or group mates..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all text-sm"
            />
          </div>

          <label className="text-sm font-medium text-gray-700 mb-2 block">
            Select People to Add
          </label>
          <div className="max-h-60 overflow-y-auto border border-gray-200 rounded-lg p-2 space-y-1">
            {isLoading ? (
              <div className="flex justify-center py-8">
                <Loader2 className="animate-spin text-blue-600" size={24} />
              </div>
            ) : filteredUsers.length > 0 ? (
              filteredUsers.map((user) => (
                <div
                  key={user.id}
                  onClick={() => toggleUser(user.id)}
                  className={`flex items-center justify-between p-2 rounded-md cursor-pointer transition-colors ${
                    selectedUserIds.includes(user.id)
                      ? 'bg-blue-50 border-blue-100 border'
                      : 'hover:bg-gray-50'
                  }`}
                >
                  <div className="flex items-center gap-2 overflow-hidden">
                    <div className="w-8 h-8 flex-shrink-0 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 text-xs font-bold">
                      {user.firstName[0]}
                      {user.lastName ? user.lastName[0] : ''}
                    </div>
                    <div className="flex flex-col min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-900 truncate">
                          {user.firstName} {user.lastName}
                        </span>
                        {user.isTemp && (
                          <Badge variant="temp" className="text-[10px] py-0 px-1">Temp Friend</Badge>
                        )}
                      </div>
                      <span className="text-xs text-gray-500 truncate">@{user.username}</span>
                    </div>
                  </div>
                  {selectedUserIds.includes(user.id) ? (
                    <CheckCircle2 size={18} className="text-blue-600 flex-shrink-0" />
                  ) : (
                    <UserPlus size={18} className="text-gray-400 flex-shrink-0" />
                  )}
                </div>
              ))
            ) : (
              <div className="text-center py-8">
                <p className="text-sm text-gray-500 mb-4">
                  {searchQuery 
                    ? `No results found for "${searchQuery}"` 
                    : "No one available to add. Invite more friends to split expenses!"}
                </p>
                <Link 
                  to="/friends" 
                  className="inline-flex items-center gap-1.5 text-blue-600 hover:text-blue-700 font-medium text-sm transition-colors"
                  onClick={onClose}
                >
                  <span>Go to Friends Page</span>
                  <ExternalLink size={14} />
                </Link>
              </div>
            )}
          </div>
        </div>

        <div className="flex gap-3 pt-2">
          <Button type="button" variant="ghost" className="flex-1 border border-gray-300" onClick={handleClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            className="flex-1"
            disabled={selectedUserIds.length === 0 || bulkAddMutation.isPending}
          >
            {bulkAddMutation.isPending ? (
              <>
                <Loader2 size={18} className="animate-spin mr-2" />
                Adding...
              </>
            ) : (
              `Add ${selectedUserIds.length > 0 ? selectedUserIds.length : ''} Member${selectedUserIds.length !== 1 ? 's' : ''}`
            )}
          </Button>
        </div>
      </form>
    </Modal>
  );
};

export default AddMemberModal;
