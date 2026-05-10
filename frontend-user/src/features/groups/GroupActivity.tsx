import { useQuery } from "@tanstack/react-query";
import { expenseService } from "../expenses/expenseService";
import { settlementService } from "../balances/settlementService";
import type { Settlement } from "../balances/settlementService";
import { useAuthStore } from "../../store/authStore";
import { Card, CardContent } from "../../components/core/Card/Card";
import Button from "../../components/core/Button/Button";
import {
  Loader2,
  Receipt,
  ArrowUpRight,
  ArrowDownLeft,
  Plus,
  HandCoins,
} from "lucide-react";
import type { GroupBalanceResponse } from "../../types/group";
import type { Expense } from "../../types/expense";

interface GroupActivityProps {
  groupId: number;
  balancesResponse?: GroupBalanceResponse;
  onAddExpense?: () => void;
}

type ActivityItem = 
  | { type: "expense"; data: Expense; date: Date }
  | { type: "settlement"; data: Settlement; date: Date };

const GroupActivity = ({
  groupId,
  balancesResponse,
  onAddExpense,
}: GroupActivityProps) => {
  const { user } = useAuthStore();
  const currentUserId = Number(user?.id);

  const { data: expenses, isLoading: isLoadingExpenses } = useQuery({
    queryKey: ["expenses", groupId],
    queryFn: () => expenseService.getGroupExpenses(groupId),
  });

  const { data: settlements, isLoading: isLoadingSettlements } = useQuery({
    queryKey: ["group-settlements", groupId],
    queryFn: () => settlementService.getSettlementsByGroup(groupId),
  });

  if (isLoadingExpenses || isLoadingSettlements) {
    return (
      <div className="flex justify-center p-8">
        <Loader2 className="animate-spin text-blue-600" />
      </div>
    );
  }

  const activities: ActivityItem[] = [
    ...(expenses?.map(e => ({ type: "expense" as const, data: e, date: new Date(e.expenseDate) })) || []),
    ...(settlements?.map(s => ({ type: "settlement" as const, data: s, date: new Date(s.createdAt || new Date()) })) || [])
  ].sort((a, b) => b.date.getTime() - a.date.getTime());

  if (activities.length === 0) {
    return (
      <Card>
        <CardContent className="py-12 text-center">
          <Receipt className="mx-auto text-gray-300 mb-4" size={48} />
          <h3 className="text-lg font-medium text-gray-900 mb-1">
            No activity yet
          </h3>
          <p className="text-gray-500 italic mb-6">
            Add an expense or record a payment to get started!
          </p>
          {onAddExpense && (
            <Button
              onClick={onAddExpense}
              className="flex items-center gap-2 mx-auto"
            >
              <Plus size={18} />
              <span>Add Expense</span>
            </Button>
          )}
        </CardContent>
      </Card>
    );
  }

  const getMemberName = (userId: number) => {
    const member = balancesResponse?.balances.find((b) => b.userId === userId);
    return member ? `${member.firstName} ${member.lastName}` : `User ${userId}`;
  };

  return (
    <div className="space-y-4">
      {activities.map((activity) => {
        if (activity.type === "expense") {
          const expense = activity.data;
          const isPayer = expense.paidBy === currentUserId;
          const mySplit = expense.splits.find((s) => s.userId === currentUserId);

          let statusText = "not involved";
          let statusColor = "text-gray-500";
          let amountText = "";
          let Icon = Receipt;

          if (isPayer) {
            const totalOwedToMe = expense.amount - (mySplit?.shareAmount || 0);
            if (totalOwedToMe > 0) {
              statusText = "you are owed";
              statusColor = "text-green-600";
              amountText = `$${totalOwedToMe.toFixed(2)}`;
              Icon = ArrowUpRight;
            } else {
              statusText = "you paid for yourself";
              amountText = `$${expense.amount.toFixed(2)}`;
            }
          } else if (mySplit) {
            statusText = "you owe";
            statusColor = "text-red-600";
            amountText = `$${mySplit.shareAmount.toFixed(2)}`;
            Icon = ArrowDownLeft;
          }

          return (
            <Card key={`expense-${expense.id}`} className="hover:shadow-md transition-shadow">
              <CardContent className="p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4">
                    <div
                      className={`p-2 rounded-full ${isPayer ? "bg-green-100 text-green-700" : mySplit ? "bg-red-100 text-red-700" : "bg-gray-100 text-gray-700"}`}
                    >
                      <Icon size={20} />
                    </div>
                    <div>
                      <h3 className="font-semibold text-gray-900">
                        {expense.description}
                      </h3>
                      <p className="text-sm text-gray-500">
                        Paid by{" "}
                        <span className="font-medium text-gray-700">
                          {isPayer ? "You" : getMemberName(expense.paidBy)}
                        </span>{" "}
                        on {activity.date.toLocaleDateString()}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-sm text-gray-500 uppercase font-medium tracking-wider">
                      {statusText}
                    </p>
                    <p className={`text-lg font-bold ${statusColor}`}>
                      {amountText || `$${expense.amount.toFixed(2)}`}
                    </p>
                  </div>
                </div>
              </CardContent>
            </Card>
          );
        } else {
          // Settlement
          const settlement = activity.data;
          const isSender = settlement.payerId === currentUserId;
          const isReceiver = settlement.payeeId === currentUserId;
          
          let statusText = "payment";
          let statusColor = "text-gray-500";
          const amountText = `$${settlement.amount.toFixed(2)}`;

          if (isSender) {
             statusText = "you paid";
             statusColor = "text-green-600";
          } else if (isReceiver) {
             statusText = "you received";
             statusColor = "text-blue-600";
          }

          return (
            <Card key={`settlement-${settlement.id}`} className="hover:shadow-md transition-shadow border-blue-100">
              <CardContent className="p-4 bg-blue-50/30">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4">
                    <div className="p-2 rounded-full bg-blue-100 text-blue-700">
                      <HandCoins size={20} />
                    </div>
                    <div>
                      <h3 className="font-semibold text-gray-900">
                        Shared Activity
                      </h3>
                      <p className="text-sm text-gray-500">
                        <span className="font-medium text-gray-700">
                          {isSender ? "You" : getMemberName(settlement.payerId)}
                        </span>{" "}
                        paid{" "}
                        <span className="font-medium text-gray-700">
                          {isReceiver ? "You" : getMemberName(settlement.payeeId)}
                        </span>{" "}
                        on {activity.date.toLocaleDateString()}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-sm text-gray-500 uppercase font-medium tracking-wider">
                      {statusText}
                    </p>
                    <p className={`text-lg font-bold ${statusColor}`}>
                      {amountText}
                    </p>
                    {settlement.status !== "CONFIRMED" && (
                      <p className="text-xs text-amber-600 mt-1 font-medium">Pending</p>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>
          );
        }
      })}
    </div>
  );
};

export default GroupActivity;
