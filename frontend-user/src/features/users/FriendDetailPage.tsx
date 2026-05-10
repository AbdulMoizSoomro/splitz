import { useMemo, useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Loader2,
  Mail,
  User as UserIcon,
  Users,
  Receipt,
  ArrowLeft,
  DollarSign,
  TrendingUp,
  TrendingDown,
} from "lucide-react";
import api from "../../lib/axios";
import Button from "../../components/core/Button/Button";
import { groupService } from "../groups/groupService";
import { expenseService } from "../expenses/expenseService";
import { friendService } from "./friendService";
import { useAuthStore } from "../../store/authStore";
import type { User } from "../../types/user";
import DashboardLayout from "../../components/layout/DashboardLayout";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "../../components/core/Card/Card";
import FriendshipSettlementModal from "./FriendshipSettlementModal";

const FriendDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const friendId = Number(id);
  const { user: currentUser } = useAuthStore();
  const [isSettlementModalOpen, setIsSettlementModalOpen] = useState(false);

  const { data: friend, isLoading: isLoadingFriend } = useQuery({
    queryKey: ["users", id],
    queryFn: async () => {
      const response = await api.get<User>(`/users/${id}`);
      return response.data;
    },
    enabled: !!id,
  });

  const { data: balanceData, isLoading: isLoadingBalance } = useQuery({
    queryKey: ["friend-balance", currentUser?.id, friendId],
    queryFn: () =>
      friendService.getNetBalance(Number(currentUser!.id), friendId),
    enabled: !!currentUser && !!friendId,
  });

  const { data: groups, isLoading: isLoadingGroups } = useQuery({
    queryKey: ["groups"],
    queryFn: () => groupService.getGroups(),
  });

  const sharedGroups = useMemo(
    () =>
      groups?.filter((group) =>
        group.members.some((member) => member.userId === friendId),
      ) || [],
    [groups, friendId],
  );

  const { data: sharedExpenses, isLoading: isLoadingExpenses } = useQuery({
    queryKey: ["shared-expenses", id, sharedGroups.map((g) => g.id)],
    queryFn: async () => {
      if (sharedGroups.length === 0) return [];

      const expensePromises = sharedGroups.map((group) =>
        expenseService.getGroupExpenses(group.id),
      );

      const allExpenses = await Promise.all(expensePromises);
      const flatExpenses = allExpenses.flat();

      // Filter expenses where friend is involved (either as payer or in splits)
      const filtered = flatExpenses.filter(
        (expense) =>
          expense.paidBy === friendId ||
          expense.splits.some((split) => split.userId === friendId),
      );

      return filtered.sort(
        (a, b) =>
          new Date(b.expenseDate).getTime() - new Date(a.expenseDate).getTime(),
      );
    },
    enabled: !!groups,
  });

  const isLoading =
    isLoadingFriend || isLoadingGroups || isLoadingExpenses || isLoadingBalance;

  if (isLoading) {
    return (
      <DashboardLayout>
        <div className="flex justify-center py-12">
          <Loader2 className="animate-spin text-blue-600" size={40} />
        </div>
      </DashboardLayout>
    );
  }

  if (!friend) {
    return (
      <DashboardLayout>
        <div className="text-center py-12">
          <p className="text-gray-500">Friend not found.</p>
        </div>
      </DashboardLayout>
    );
  }

  const netBalance = balanceData?.netBalance || 0;

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <Button
            variant="ghost"
            size="sm"
            className="flex items-center gap-2 text-gray-600"
            onClick={() => navigate("/friends")}
          >
            <ArrowLeft size={18} />
            Back to Friends
          </Button>

          <Button
            variant="primary"
            className="flex items-center gap-2"
            onClick={() => setIsSettlementModalOpen(true)}
          >
            <DollarSign size={18} />
            Settle Debt
          </Button>
        </div>

        <div className="flex items-center gap-4">
          <div className="w-16 h-16 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 text-2xl font-bold">
            {friend.firstName[0]}
            {friend.lastName ? friend.lastName[0] : ""}
          </div>
          <div>
            <h1 className="text-3xl font-bold text-gray-900">
              {friend.firstName} {friend.lastName}
            </h1>
            <p className="text-gray-600 flex items-center gap-1">
              <UserIcon size={16} /> @{friend.username}
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-1 space-y-6">
            <Card className="overflow-hidden">
              <div
                className={`p-4 ${
                  netBalance > 0
                    ? "bg-green-50 text-green-700"
                    : netBalance < 0
                      ? "bg-orange-50 text-orange-700"
                      : "bg-gray-50 text-gray-700"
                }`}
              >
                <p className="text-sm font-medium uppercase tracking-wider mb-1">
                  Net Balance
                </p>
                <div className="flex items-center gap-2">
                  {netBalance > 0 ? (
                    <TrendingUp size={24} />
                  ) : netBalance < 0 ? (
                    <TrendingDown size={24} />
                  ) : (
                    <DollarSign size={24} />
                  )}
                  <span className="text-3xl font-bold">
                    {netBalance === 0 ? "" : netBalance > 0 ? "+" : ""}
                    {netBalance.toFixed(2)}
                  </span>
                </div>
                <p className="text-xs mt-2 opacity-80">
                  {netBalance > 0
                    ? `${friend.firstName} owes you`
                    : netBalance < 0
                      ? `You owe ${friend.firstName}`
                      : "You are all settled up!"}
                </p>
              </div>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Contact Information</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex items-center gap-2 text-gray-600">
                  <Mail size={18} />
                  <span>{friend.email}</span>
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="lg:col-span-2 space-y-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Shared Groups</CardTitle>
              </CardHeader>
              <CardContent>
                {sharedGroups.length > 0 ? (
                  <div className="space-y-3">
                    {sharedGroups.map((group) => (
                      <Link
                        key={group.id}
                        to={`/groups/${group.id}`}
                        className="flex items-center justify-between p-3 bg-white border border-gray-200 rounded-lg shadow-sm hover:bg-gray-50 transition-colors"
                      >
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-full bg-orange-100 flex items-center justify-center text-orange-600">
                            <Users size={20} />
                          </div>
                          <span className="font-medium text-gray-900">
                            {group.name}
                          </span>
                        </div>
                      </Link>
                    ))}
                  </div>
                ) : (
                  <p className="text-gray-500 italic">
                    No shared groups found.
                  </p>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Shared Expenses</CardTitle>
              </CardHeader>
              <CardContent>
                {sharedExpenses && sharedExpenses.length > 0 ? (
                  <div className="space-y-3">
                    {sharedExpenses.map((expense) => (
                      <div
                        key={expense.id}
                        className="flex items-center justify-between p-3 bg-white border border-gray-200 rounded-lg shadow-sm"
                      >
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center text-blue-600">
                            <Receipt size={20} />
                          </div>
                          <div>
                            <p className="font-medium text-gray-900">
                              {expense.description}
                            </p>
                            <p className="text-xs text-gray-500">
                              {new Date(
                                expense.expenseDate,
                              ).toLocaleDateString()}
                            </p>
                          </div>
                        </div>
                        <div className="text-right">
                          <p className="font-bold text-gray-900">
                            {expense.currency} {expense.amount.toFixed(2)}
                          </p>
                          <p className="text-xs text-gray-500">
                            Paid by{" "}
                            {expense.paidBy === friendId
                              ? friend.firstName
                              : "You"}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-gray-500 italic">
                    No shared expenses found.
                  </p>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      </div>

      {isSettlementModalOpen && currentUser && (
        <FriendshipSettlementModal
          isOpen={isSettlementModalOpen}
          onClose={() => setIsSettlementModalOpen(false)}
          currentUser={{
            id: Number(currentUser.id),
            username: currentUser.username,
            email: currentUser.email,
            firstName: "", // We don't have first/last name in authStore user object likely
            lastName: "",
          }}
          friend={friend}
          suggestedAmount={netBalance}
        />
      )}
    </DashboardLayout>
  );
};

export default FriendDetailPage;
