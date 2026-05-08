import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { groupService } from './groupService';
import { useAuthStore } from '../../store/authStore';
import DashboardLayout from '../../components/layout/DashboardLayout';
import { Card, CardHeader, CardTitle, CardContent } from '../../components/core/Card/Card';
import Button from '../../components/core/Button/Button';
import Modal from '../../components/core/Modal/Modal';
import { Loader2, ArrowLeft, LogOut } from 'lucide-react';

const GroupDetails = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuthStore();
  const [isLeaveModalOpen, setIsLeaveModalOpen] = useState(false);

  const { data: group, isLoading } = useQuery({
    queryKey: ['group', id],
    queryFn: () => groupService.getGroup(Number(id)),
    enabled: !!id,
  });

  const { data: balancesResponse, isLoading: isBalancesLoading } = useQuery({
    queryKey: ['group-balances', id],
    queryFn: () => groupService.getBalances(Number(id)),
    enabled: !!id && isLeaveModalOpen,
  });

  const leaveMutation = useMutation({
    mutationFn: () => groupService.removeMember(Number(id), Number(user?.id)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups'] });
      navigate('/groups');
    },
  });

  const handleLeave = () => {
    leaveMutation.mutate();
  };

  const currentUserBalance = balancesResponse?.balances.find(b => b.userId === Number(user?.id))?.balance ?? 0;
  const canLeave = currentUserBalance === 0;

  if (isLoading) {
    return (
      <DashboardLayout>
        <div className="flex justify-center py-12">
          <Loader2 className="animate-spin text-blue-600" size={48} />
        </div>
      </DashboardLayout>
    );
  }

  if (!group) {
    return (
      <DashboardLayout>
        <div className="text-center py-12">
          <h2 className="text-xl font-bold text-gray-900">Group not found</h2>
          <Button onClick={() => navigate('/groups')} className="mt-4">
            Back to Groups
          </Button>
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="sm" onClick={() => navigate('/groups')}>
            <ArrowLeft size={20} />
          </Button>
          <h1 className="text-2xl font-bold text-gray-900">{group.name}</h1>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>About this Group</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-gray-600">{group.description || 'No description provided.'}</p>
              </CardContent>
            </Card>
          </div>

          <div className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>Actions</CardTitle>
              </CardHeader>
              <CardContent>
                <Button
                  variant="secondary"
                  onClick={() => setIsLeaveModalOpen(true)}
                  className="w-full flex items-center justify-center gap-2 text-red-600 border-red-200 hover:bg-red-50 hover:text-red-700"
                >
                  <LogOut size={18} />
                  <span>Leave Group</span>
                </Button>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>

      <Modal
        isOpen={isLeaveModalOpen}
        onClose={() => setIsLeaveModalOpen(false)}
        title="Leave Group"
      >
        <div className="space-y-4">
          {isBalancesLoading ? (
            <div className="flex justify-center py-4">
              <Loader2 className="animate-spin text-blue-600" size={24} />
            </div>
          ) : !canLeave ? (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
              You cannot leave this group while you have an outstanding balance ({currentUserBalance}).
            </div>
          ) : (
            <p className="text-gray-600">
              Are you sure you want to leave this group? You will no longer be able to see expenses or add new ones.
            </p>
          )}
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setIsLeaveModalOpen(false)} disabled={leaveMutation.isPending}>
              Cancel
            </Button>
            <Button 
              variant="danger" 
              onClick={handleLeave} 
              disabled={leaveMutation.isPending || isBalancesLoading || !canLeave}
            >
              {leaveMutation.isPending ? 'Leaving...' : 'Leave Group'}
            </Button>
          </div>
        </div>
      </Modal>
    </DashboardLayout>
  );
};

export default GroupDetails;
