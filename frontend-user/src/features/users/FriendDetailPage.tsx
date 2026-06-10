import { useMemo, useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
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
  Pencil,
  X,
  Check,
  Globe,
} from "lucide-react";
import api from "../../lib/axios";
import { Button } from "@/components/ui/button";
import { groupService } from "../groups/groupService";
import { expenseService } from "../expenses/expenseService";
import { friendService } from "./friendService";
import { useAuthStore } from "../../store/authStore";
import { toast } from "sonner";
import type { User, FriendshipSettlementDTO } from "../../types/user";
import type { Expense } from "../../types/expense";
import DashboardLayout from "../../components/layout/DashboardLayout";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import FriendshipSettlementModal from "./FriendshipSettlementModal";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";

// Helper type for unified activity feed
type ActivityItem = 
  | { type: 'expense'; data: Expense; date: Date }
  | { type: 'settlement'; data: FriendshipSettlementDTO; date: Date };

const FriendDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const friendId = Number(id);
  const { user: currentUser } = useAuthStore();
  const [isSettlementModalOpen, setIsSettlementModalOpen] = useState(false);
  const [editingSettlementId, setEditingSettlementId] = useState<number | null>(null);
  const [editAmount, setEditAmount] = useState("");
  const queryClient = useQueryClient();


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

  const netBalance = balanceData?.netBalance || 0;

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

  // Build a groupId -> groupName lookup map
  const groupNameMap = useMemo(() => {
    const map: Record<number, string> = {};
    if (groups) {
      groups.forEach((g) => { map[g.id] = g.name; });
    }
    return map;
  }, [groups]);

  // Compute group-specific balances lookup map
  const groupBalancesMap = useMemo(() => {
    const map: Record<number, number> = {};
    if (balanceData?.groupBalances) {
      balanceData.groupBalances.forEach((gb) => {
        map[gb.groupId] = gb.balance;
      });
    }
    return map;
  }, [balanceData]);

  // Compute the total sum of all group balances
  const groupBalancesTotal = useMemo(() => {
    return balanceData?.groupBalances?.reduce((sum, gb) => sum + gb.balance, 0) || 0;
  }, [balanceData]);

  // Direct (non-group) balance is the net total minus all group allocations
  const directBalance = useMemo(() => {
    return netBalance - groupBalancesTotal;
  }, [netBalance, groupBalancesTotal]);

  const { data: sharedExpenses, isLoading: isLoadingExpenses } = useQuery({
    queryKey: ["shared-expenses", id, sharedGroups.map((g) => g.id)],
    queryFn: async () => {
      if (sharedGroups.length === 0) return [];

      const allExpenses = await expenseService.getBulkGroupExpenses(
        sharedGroups.map((g) => g.id),
      );

      // Filter expenses where friend is involved (either as payer or in splits)
      const filtered = allExpenses.filter(
        (expense) =>
          expense.paidBy === friendId ||
          expense.splits.some((split) => split.userId === friendId),
      );

      return filtered;
    },
    enabled: !!groups,
  });

  const { data: settlements, isLoading: isLoadingSettlements } = useQuery({
    queryKey: ["friend-settlements", currentUser?.id, friendId],
    queryFn: () =>
      friendService.getSettlementsWithFriend(Number(currentUser!.id), friendId),
    enabled: !!currentUser && !!friendId,
  });

  const confirmMutation = useMutation({
    mutationFn: (settlementId: number) => friendService.confirmSettlement(settlementId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["friend-balance"] });
      queryClient.invalidateQueries({ queryKey: ["friend-settlements"] });
      toast.success("Payment confirmed");
    },
    onError: () => {
      toast.error("Failed to confirm payment");
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ settlementId, amount }: { settlementId: number; amount: number }) =>
      friendService.updateSettlement(settlementId, { amount }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["friend-balance"] });
      queryClient.invalidateQueries({ queryKey: ["friend-settlements"] });
      setEditingSettlementId(null);
      setEditAmount("");
      toast.success("Payment updated");
    },
    onError: () => {
      toast.error("Failed to update payment");
    },
  });

  const unifiedActivity = useMemo(() => {
    const activities: ActivityItem[] = [];

    if (sharedExpenses) {
      activities.push(
        ...sharedExpenses.map((expense) => ({
          type: 'expense' as const,
          data: expense,
          date: new Date(expense.expenseDate),
        }))
      );
    }

    if (settlements) {
      activities.push(
        ...settlements.map((settlement) => ({
          type: 'settlement' as const,
          data: settlement,
          date: new Date(settlement.createdAt),
        }))
      );
    }

    return activities.sort((a, b) => b.date.getTime() - a.date.getTime());
  }, [sharedExpenses, settlements]);


  const isLoading =
    isLoadingFriend || isLoadingGroups || isLoadingExpenses || isLoadingBalance || isLoadingSettlements;

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

  const startEditing = (settlement: FriendshipSettlementDTO) => {
    setEditingSettlementId(settlement.id);
    setEditAmount(settlement.amount.toFixed(2));
  };

  const cancelEditing = () => {
    setEditingSettlementId(null);
    setEditAmount("");
  };

  const submitEdit = (settlementId: number) => {
    const parsedAmount = parseFloat(editAmount);
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      toast.error("Please enter a valid amount");
      return;
    }
    updateMutation.mutate({ settlementId, amount: parsedAmount });
  };

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
            variant="default"
            className="flex items-center gap-2"
            onClick={() => setIsSettlementModalOpen(true)}
          >
            <DollarSign size={18} />
            Settle Debt
          </Button>
        </div>

        <div className="flex items-center gap-4">
          <div className="w-16 h-16 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 text-2xl font-bold">
            {friend.firstName ? friend.firstName[0] : ""}
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
              <CardHeader className="pb-2">
                <CardTitle className="text-lg font-bold text-gray-900">Balance Breakdown</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {/* Direct Personal Balance Row */}
                <div className="flex items-center justify-between p-3 bg-indigo-50/40 border border-indigo-100/60 rounded-xl transition-all hover:bg-indigo-50/80">
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-xl bg-indigo-100 flex items-center justify-center text-indigo-600 shadow-sm shrink-0">
                      <Globe size={18} />
                    </div>
                    <div>
                      <span className="font-semibold text-gray-950 block text-xs tracking-tight">
                        Direct Balance
                      </span>
                      <span className="text-[10px] text-gray-500 font-medium">
                        Personal Settlements
                      </span>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className={`text-sm font-bold tracking-tight ${
                      directBalance > 0 ? "text-emerald-600" : directBalance < 0 ? "text-rose-600" : "text-gray-500"
                    }`}>
                      {directBalance === 0 ? "$0.00" : directBalance > 0 ? `+$${directBalance.toFixed(2)}` : `-$${Math.abs(directBalance).toFixed(2)}`}
                    </span>
                  </div>
                </div>

                {/* Group Balances List */}
                {sharedGroups.length > 0 && (
                  <div className="pt-2 border-t border-gray-100 space-y-2">
                    <p className="text-[11px] font-bold text-gray-400 uppercase tracking-wider px-1">Group Splits</p>
                    {sharedGroups.map((group) => {
                      const bal = groupBalancesMap[group.id] || 0;
                      return (
                        <div key={group.id} className="flex items-center justify-between p-2.5 hover:bg-gray-50/80 rounded-lg transition-colors">
                          <span className="text-xs font-semibold text-gray-700 truncate max-w-[130px]" title={group.name}>
                            {group.name}
                          </span>
                          <span className={`text-xs font-bold ${
                            bal > 0 ? "text-emerald-600" : bal < 0 ? "text-rose-600" : "text-gray-500"
                          }`}>
                            {bal === 0 ? "$0.00" : bal > 0 ? `+$${bal.toFixed(2)}` : `-$${Math.abs(bal).toFixed(2)}`}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                )}
              </CardContent>
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
              <CardTitle className="text-lg">Shared Activity</CardTitle>
            </CardHeader>
            <CardContent>
              {unifiedActivity.length > 0 ? (
                <div className="space-y-3">
                  {unifiedActivity.map((activity, index) => {
                    if (activity.type === 'expense') {
                      const expense = activity.data as Expense;
                      return (
                        <div
                          key={`expense-${expense.id}-${index}`}
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
                      );
                    } else {
                      const settlement = activity.data as FriendshipSettlementDTO;
                      const isPayer = settlement.payerId === Number(currentUser?.id);
                      const isEditing = editingSettlementId === settlement.id;
                      const canEdit = settlement.status !== 'COMPLETED';

                      let badgeClassName = "bg-gray-100 text-gray-800 hover:bg-gray-100/80 border-gray-200";
                      if (settlement.status === 'COMPLETED') {
                        badgeClassName = "bg-green-100 text-green-800 border-green-200 hover:bg-green-100/80";
                      } else if (settlement.status === 'MARKED_PAID') {
                        badgeClassName = "bg-yellow-100 text-yellow-800 border-yellow-200 hover:bg-yellow-100/80";
                      }

                      const isGlobalPayment = !settlement.allocations || 
                                              settlement.allocations.length === 0 || 
                                              settlement.allocations.some(a => !a.groupId);

                      return (
                        <div
                          key={`settlement-${settlement.id}-${index}`}
                          className="p-3 bg-white border border-gray-200 rounded-lg shadow-sm hover:shadow-md transition-shadow"
                        >
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-3">
                              <div className="w-10 h-10 rounded-full bg-green-100 flex items-center justify-center text-green-600">
                                <DollarSign size={20} />
                              </div>
                              <div className="flex-1">
                                <div className="flex items-center gap-2 flex-wrap">
                                  <p className="font-medium text-gray-900">
                                    {isPayer ? `You paid ${friend.firstName}` : `${friend.firstName} paid you`}
                                  </p>
                                  <Badge className={badgeClassName}>
                                    {settlement.status === 'MARKED_PAID' ? 'Pending Confirmation' :
                                     settlement.status === 'COMPLETED' ? 'Settled' : 'Pending'}
                                  </Badge>
                                  {isGlobalPayment && (
                                    <span className="px-2 py-0.5 text-[9px] font-bold rounded bg-indigo-50 text-indigo-700 border border-indigo-100 flex items-center gap-1 shadow-sm uppercase tracking-wider">
                                      <Globe size={10} /> Direct
                                    </span>
                                  )}
                                  {!isPayer && settlement.status === 'MARKED_PAID' && (
                                    <Button
                                      variant="default"
                                      size="sm"
                                      onClick={() => confirmMutation.mutate(settlement.id)}
                                      disabled={confirmMutation.isPending}
                                      className="h-6 py-0 px-2 text-[10px]"
                                    >
                                      {confirmMutation.isPending ? <Loader2 className="animate-spin" size={12} /> : 'Confirm Receipt'}
                                    </Button>
                                  )}
                                  {canEdit && !isEditing && (
                                    <button
                                      onClick={() => startEditing(settlement)}
                                      className="p-1 text-gray-400 hover:text-blue-600 transition-colors rounded"
                                      title="Edit payment"
                                    >
                                      <Pencil size={14} />
                                    </button>
                                  )}
                                </div>
                                <p className="text-xs text-gray-500">
                                  {new Date(settlement.createdAt).toLocaleDateString()}{" "}
                                  {new Date(settlement.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                </p>
                              </div>
                            </div>
                            <div className="text-right">
                              {isEditing ? (
                                <div className="flex items-center gap-1">
                                  <span className="text-gray-500">$</span>
                                  <Input
                                    type="number"
                                    step="0.01"
                                    min="0.01"
                                    value={editAmount}
                                    onChange={(e) => setEditAmount(e.target.value)}
                                    className="w-20 text-right text-sm"
                                    autoFocus
                                  />
                                  <button
                                    onClick={() => submitEdit(settlement.id)}
                                    disabled={updateMutation.isPending}
                                    className="p-1 text-green-600 hover:text-green-700 transition-colors"
                                    title="Save"
                                  >
                                    {updateMutation.isPending ? <Loader2 className="animate-spin" size={14} /> : <Check size={14} />}
                                  </button>
                                  <button
                                    onClick={cancelEditing}
                                    className="p-1 text-red-500 hover:text-red-600 transition-colors"
                                    title="Cancel"
                                  >
                                    <X size={14} />
                                  </button>
                                </div>
                              ) : (
                                <p className="font-bold text-gray-900">
                                  ${settlement.amount.toFixed(2)}
                                </p>
                              )}
                            </div>
                          </div>

                          {/* Group Allocations */}
                          {settlement.allocations && settlement.allocations.length > 0 ? (
                            <div className="mt-2 ml-13 pl-3 border-l-2 border-gray-100 space-y-1">
                              {settlement.allocations.map((alloc, aIdx) => (
                                <div key={aIdx} className="flex items-center justify-between text-xs text-gray-500 py-0.5">
                                  <span className="flex items-center gap-1.5">
                                    {alloc.groupId ? (
                                      <>
                                        <Users size={10} className="text-gray-400" />
                                        <Link
                                          to={`/groups/${alloc.groupId}`}
                                          className="text-blue-600 hover:underline"
                                        >
                                          {groupNameMap[alloc.groupId] || `Group #${alloc.groupId}`}
                                        </Link>
                                      </>
                                    ) : (
                                      <>
                                        <Globe size={10} className="text-indigo-500" />
                                        <span className="text-indigo-600 font-semibold bg-indigo-50 px-1.5 py-0.5 rounded text-[10px]">
                                          Direct Personal Balance
                                        </span>
                                      </>
                                    )}
                                  </span>
                                  <span className="font-medium text-gray-600">
                                    ${alloc.amount.toFixed(2)}
                                  </span>
                                </div>
                              ))}
                            </div>
                          ) : (
                            /* Entirely Direct Payment indicator when no allocations array exists */
                            <div className="mt-2 ml-13 pl-3 border-l-2 border-gray-100">
                              <div className="flex items-center justify-between text-xs text-gray-500 py-0.5">
                                <span className="flex items-center gap-1.5">
                                  <Globe size={10} className="text-indigo-500" />
                                  <span className="text-indigo-600 font-semibold bg-indigo-50 px-1.5 py-0.5 rounded text-[10px]">
                                    Direct Personal Balance
                                  </span>
                                </span>
                                <span className="font-medium text-gray-600">
                                  ${settlement.amount.toFixed(2)}
                                </span>
                              </div>
                            </div>
                          )}
                        </div>
                      );
                    }
                  })}
                </div>
              ) : (
                <p className="text-gray-500 italic">
                  No shared activity found.
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
            firstName: currentUser.username, // Use username as fallback for first name
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
