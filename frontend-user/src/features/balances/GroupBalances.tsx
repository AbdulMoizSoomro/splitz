import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { settlementService } from './settlementService';
import { groupService } from '../groups/groupService';
import { useAuthStore } from '../../store/authStore';
import { useToastStore } from '../../store/toastStore';
import { Card, CardHeader, CardTitle, CardContent } from '../../components/core/Card/Card';
import Button from '../../components/core/Button/Button';
import { Loader2, ArrowRight, CheckCircle2, Clock } from 'lucide-react';
import { useState } from 'react';
import Modal from '../../components/core/Modal/Modal';

interface GroupBalancesProps {
  groupId: number;
}

const GroupBalances = ({ groupId }: GroupBalancesProps) => {
  const { user } = useAuthStore();
  const currentUserId = Number(user?.id);
  const queryClient = useQueryClient();
  const { addToast } = useToastStore();
  const [isSettleModalOpen, setIsAddSettleModalOpen] = useState(false);
  const [selectedDebt, setSelectedDebt] = useState<{ from: number, to: number, amount: number, toUsername: string } | null>(null);

  const { data: balances, isLoading } = useQuery({
    queryKey: ['group-balances', groupId],
    queryFn: () => groupService.getBalances(groupId),
  });

  const { data: settlements, isLoading: isLoadingSettlements } = useQuery({
    queryKey: ['group-settlements', groupId],
    queryFn: () => settlementService.getSettlementsByGroup(groupId),
  });

  const createSettlementMutation = useMutation({
    mutationFn: async (debt: { to: number, amount: number }) => {
      const s = await settlementService.createSettlement({
        fromUserId: currentUserId,
        toUserId: debt.to,
        amount: debt.amount,
        currency: 'USD', // Default to USD for now or get from group
        groupId
      });
      return settlementService.markAsPaid(s.id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group-balances', groupId] });
      queryClient.invalidateQueries({ queryKey: ['group-settlements', groupId] });
      addToast('Payment recorded and marked as paid', 'success');
      setIsAddSettleModalOpen(false);
    },
    onError: () => {
      addToast('Failed to record payment', 'error');
    }
  });

  const confirmMutation = useMutation({
    mutationFn: (id: number) => settlementService.confirmSettlement(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group-balances', groupId] });
      queryClient.invalidateQueries({ queryKey: ['group-settlements', groupId] });
      addToast('Payment confirmed', 'success');
    }
  });

  if (isLoading || isLoadingSettlements) {
    return <div className="flex justify-center p-8"><Loader2 className="animate-spin" /></div>;
  }

  const userDebts = balances?.simplifiedDebts.filter(d => d.from === currentUserId) || [];
  const userOwed = balances?.simplifiedDebts.filter(d => d.to === currentUserId) || [];
  
  const pendingIncoming = settlements?.filter(s => s.toUserId === currentUserId && s.status === 'PAID') || [];
  const pendingOutgoing = settlements?.filter(s => s.fromUserId === currentUserId && s.status === 'PAID') || [];

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* You Owe */}
        <Card>
          <CardHeader>
            <CardTitle className="text-red-600">You Owe</CardTitle>
          </CardHeader>
          <CardContent>
            {userDebts.length === 0 ? (
              <p className="text-gray-500 text-sm italic">You don't owe anything!</p>
            ) : (
              <div className="space-y-3">
                {userDebts.map((debt, idx) => (
                  <div key={idx} className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium">To {debt.toUsername || `User ${debt.to}`}</p>
                      <p className="text-lg font-bold text-red-600">${debt.amount.toFixed(2)}</p>
                    </div>
                    <Button 
                      size="sm" 
                      onClick={() => {
                        setSelectedDebt({ ...debt, toUsername: debt.toUsername || `User ${debt.to}` });
                        setIsAddSettleModalOpen(true);
                      }}
                    >
                      Settle
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* You Are Owed */}
        <Card>
          <CardHeader>
            <CardTitle className="text-green-600">You Are Owed</CardTitle>
          </CardHeader>
          <CardContent>
            {userOwed.length === 0 ? (
              <p className="text-gray-500 text-sm italic">No one owes you.</p>
            ) : (
              <div className="space-y-3">
                {userOwed.map((debt, idx) => (
                  <div key={idx} className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium">From {debt.fromUsername || `User ${debt.from}`}</p>
                      <p className="text-lg font-bold text-green-600">${debt.amount.toFixed(2)}</p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Pending Confirmations */}
      {(pendingIncoming.length > 0 || pendingOutgoing.length > 0) && (
        <Card>
          <CardHeader>
            <CardTitle>Pending Confirmations</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {pendingIncoming.map(s => (
                <div key={s.id} className="flex items-center justify-between p-3 bg-amber-50 rounded-lg border border-amber-100">
                  <div className="flex items-center gap-3">
                    <Clock className="text-amber-600" size={20} />
                    <div>
                      <p className="text-sm font-medium">User {s.fromUserId} sent you ${s.amount.toFixed(2)}</p>
                      <p className="text-xs text-amber-700">Waiting for your confirmation</p>
                    </div>
                  </div>
                  <Button 
                    variant="primary" 
                    size="sm" 
                    onClick={() => confirmMutation.mutate(s.id)}
                    disabled={confirmMutation.isPending}
                  >
                    Confirm Receipt
                  </Button>
                </div>
              ))}
              {pendingOutgoing.map(s => (
                <div key={s.id} className="flex items-center gap-3 p-3 bg-blue-50 rounded-lg border border-blue-100">
                  <Clock className="text-blue-600" size={20} />
                  <div>
                    <p className="text-sm font-medium">You sent ${s.amount.toFixed(2)} to User {s.toUserId}</p>
                    <p className="text-xs text-blue-700">Waiting for confirmation</p>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Settle Modal */}
      <Modal 
        isOpen={isSettleModalOpen} 
        onClose={() => setIsAddSettleModalOpen(false)}
        title="Record Payment"
      >
        <div className="space-y-4">
          <p className="text-gray-600">
            Confirm that you have sent <strong>${selectedDebt?.amount.toFixed(2)}</strong> to <strong>{selectedDebt?.toUsername}</strong>.
          </p>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setIsAddSettleModalOpen(false)}>Cancel</Button>
            <Button 
              onClick={() => selectedDebt && createSettlementMutation.mutate(selectedDebt)}
              disabled={createSettlementMutation.isPending}
            >
              {createSettlementMutation.isPending ? 'Processing...' : 'Confirm & Mark Paid'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default GroupBalances;
