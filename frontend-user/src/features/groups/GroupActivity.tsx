import { useQuery } from '@tanstack/react-query';
import { expenseService } from '../expenses/expenseService';
import { useAuthStore } from '../../store/authStore';
import { Card, CardContent } from '../../components/core/Card/Card';
import { Loader2, Receipt, ArrowUpRight, ArrowDownLeft } from 'lucide-react';
import type { GroupBalanceResponse } from '../../types/group';

interface GroupActivityProps {
  groupId: number;
  balancesResponse?: GroupBalanceResponse;
}

const GroupActivity = ({ groupId, balancesResponse }: GroupActivityProps) => {
  const { user } = useAuthStore();
  const currentUserId = Number(user?.id);

  const { data: expenses, isLoading } = useQuery({
    queryKey: ['group-expenses', groupId],
    queryFn: () => expenseService.getGroupExpenses(groupId),
  });

  if (isLoading) {
    return <div className="flex justify-center p-8"><Loader2 className="animate-spin text-blue-600" /></div>;
  }

  if (!expenses || expenses.length === 0) {
    return (
      <Card>
        <CardContent className="py-12 text-center">
          <Receipt className="mx-auto text-gray-300 mb-4" size={48} />
          <p className="text-gray-500 italic">No activity yet. Add an expense to get started!</p>
        </CardContent>
      </Card>
    );
  }

  const getMemberName = (userId: number) => {
    const member = balancesResponse?.balances.find(b => b.userId === userId);
    return member ? `${member.firstName} ${member.lastName}` : `User ${userId}`;
  };

  // Sort expenses by date (newest first)
  const sortedExpenses = [...expenses].sort((a, b) => 
    new Date(b.expenseDate).getTime() - new Date(a.expenseDate).getTime()
  );

  return (
    <div className="space-y-4">
      {sortedExpenses.map((expense) => {
        const isPayer = expense.paidBy === currentUserId;
        const mySplit = expense.splits.find(s => s.userId === currentUserId);
        
        let statusText = 'not involved';
        let statusColor = 'text-gray-500';
        let amountText = '';
        let Icon = Receipt;

        if (isPayer) {
          const totalOwedToMe = expense.amount - (mySplit?.shareAmount || 0);
          if (totalOwedToMe > 0) {
            statusText = 'you are owed';
            statusColor = 'text-green-600';
            amountText = `$${totalOwedToMe.toFixed(2)}`;
            Icon = ArrowUpRight;
          } else {
            statusText = 'you paid for yourself';
            amountText = `$${expense.amount.toFixed(2)}`;
          }
        } else if (mySplit) {
          statusText = 'you owe';
          statusColor = 'text-red-600';
          amountText = `$${mySplit.shareAmount.toFixed(2)}`;
          Icon = ArrowDownLeft;
        }

        return (
          <Card key={expense.id} className="hover:shadow-md transition-shadow">
            <CardContent className="p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                  <div className={`p-2 rounded-full ${isPayer ? 'bg-green-100 text-green-700' : mySplit ? 'bg-red-100 text-red-700' : 'bg-gray-100 text-gray-700'}`}>
                    <Icon size={20} />
                  </div>
                  <div>
                    <h3 className="font-semibold text-gray-900">{expense.description}</h3>
                    <p className="text-sm text-gray-500">
                      Paid by <span className="font-medium text-gray-700">{isPayer ? 'You' : getMemberName(expense.paidBy)}</span> on {new Date(expense.expenseDate).toLocaleDateString()}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-sm text-gray-500 uppercase font-medium tracking-wider">{statusText}</p>
                  <p className={`text-lg font-bold ${statusColor}`}>{amountText || `$${expense.amount.toFixed(2)}`}</p>
                </div>
              </div>
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
};

export default GroupActivity;
