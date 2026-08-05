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
  UserPlus,
  UserMinus,
} from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import api from "../../lib/axios";
import { Button } from "@/components/ui/button";
import { useInterpersonalFriend } from "./interpersonal";
import { isGlobalPayment as isGlobalPaymentFor, confirmPayment as confirmPaymentFor } from "../balances/settlement";
import { queryKeys, invalidations, bindInvalidations } from "../../lib/queryKeys";
import { useAuthStore } from "../../store/authStore";
import { toast } from "sonner";
import type { User, FriendshipSettlementDTO } from "../../types/user";
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

const FriendDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const friendId = Number(id);
  const { user: currentUser } = useAuthStore();
  const [isSettlementModalOpen, setIsSettlementModalOpen] = useState(false);
  const [editingSettlementId, setEditingSettlementId] = useState<number | null>(null);
  const [editAmount, setEditAmount] = useState("");
  const [isAddFriendModalOpen, setIsAddFriendModalOpen] = useState(false);
  const [isCancelRequestModalOpen, setIsCancelRequestModalOpen] = useState(false);
  const queryClient = useQueryClient();
  const invalidate = bindInvalidations(queryClient);

  const {
    friend,
    relationshipStatus,
    netBalance,
    directBalance,
    groupBalances,
    sharedGroups,
    activityFeed: unifiedActivity,
    isLoading,
    mutations,
  } = useInterpersonalFriend(friendId);

  const isConfirmedFriend = relationshipStatus === "CONFIRMED_FRIEND";
  const isPendingOutgoing = relationshipStatus === "PENDING_OUTGOING";
  const hasLoadedStatus = relationshipStatus !== undefined;

  const addFriendMutation = {
    mutate: () => {
      mutations.sendFriendRequest.mutate(undefined, {
        onSuccess: () => {
          setIsAddFriendModalOpen(false);
          toast.success("Friend request sent");
        },
      });
    },
    isPending: mutations.sendFriendRequest.isPending,
  };

  const cancelRequestMutation = {
    mutate: () => {
      mutations.cancelFriendRequest.mutate(undefined, {
        onSuccess: () => {
          setIsCancelRequestModalOpen(false);
          toast.success("Friend request cancelled");
        },
      });
    },
    isPending: mutations.cancelFriendRequest.isPending,
  };

  const groupNameMap = useMemo(() => {
    const map: Record<number, string> = {};
    sharedGroups.forEach((g) => {
      map[g.id] = g.name;
    });
    return map;
  }, [sharedGroups]);

  const groupBalancesMap = useMemo(() => {
    const map: Record<number, number> = {};
    groupBalances.forEach((gb) => {
      map[gb.groupId] = gb.balance;
    });
    return map;
  }, [groupBalances]);

  const confirmMutation = {
    mutate: (settlementId: number) => {
      mutations.confirmSettlement.mutate(settlementId, {
        onSuccess: () => toast.success("Payment confirmed"),
        onError: () => toast.error("Failed to confirm payment"),
      });
    },
    isPending: mutations.confirmSettlement.isPending,
  };

  const updateMutation = {
    mutate: ({ settlementId, amount }: { settlementId: number; amount: number }) => {
      mutations.updateSettlement.mutate(
        { settlementId, amount },
        {
          onSuccess: () => {
            setEditingSettlementId(null);
            setEditAmount("");
            toast.success("Payment updated");
          },
          onError: () => toast.error("Failed to update payment"),
        },
      );
    },
    isPending: mutations.updateSettlement.isPending,
  };

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
          <p className="text-muted-foreground">Friend not found.</p>
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
    <DashboardLayout breadcrumbs={[{ label: "Friends", href: "/friends" }, { label: `${friend.firstName} ${friend.lastName}` }]}>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <Button
            variant="ghost"
            size="sm"
            className="flex items-center gap-2 text-muted-foreground"
            onClick={() => navigate("/friends")}
          >
            <ArrowLeft size={18} />
            Back to Friends
          </Button>

          {hasLoadedStatus && !isConfirmedFriend && (
            isPendingOutgoing ? (
              <Button variant="outline" size="sm" onClick={() => setIsCancelRequestModalOpen(true)}>
                <UserMinus size={16} className="mr-2" />
                Cancel Request
              </Button>
            ) : (
              <Button variant="default" size="sm" onClick={() => setIsAddFriendModalOpen(true)}>
                <UserPlus size={16} className="mr-2" />
                Add Friend
              </Button>
            )
          )}
        </div>

        <div className="flex items-center gap-4">
          <div className="w-16 h-16 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 text-2xl font-bold">
            {friend.firstName ? friend.firstName[0] : ""}
            {friend.lastName ? friend.lastName[0] : ""}
          </div>
          <div>
            <h1 className="text-3xl font-bold text-foreground">
              {friend.firstName} {friend.lastName}
            </h1>
            <p className="text-muted-foreground flex items-center gap-1">
              <UserIcon size={16} /> @{friend.username}
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-1 space-y-6">
            <Card className="overflow-hidden">
              <CardContent
                className={`p-4 ${
                  netBalance > 0
                    ? "bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400"
                    : netBalance < 0
                      ? "bg-orange-50 dark:bg-orange-900/20 text-orange-700 dark:text-orange-400"
                      : "bg-muted text-muted-foreground"
                }`}
              >
                <p className="text-sm font-medium uppercase tracking-wider mb-2">
                  Net Balance
                </p>
                <div className="flex items-center justify-between gap-4">
                  <div>
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
                  <Button
                    variant={netBalance === 0 ? "outline" : "default"}
                    className="flex items-center gap-2 shrink-0 bg-background text-foreground hover:bg-muted"
                    onClick={() => setIsSettlementModalOpen(true)}
                  >
                    <DollarSign size={18} />
                    Settle Debt
                  </Button>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-lg font-bold text-foreground">Balance Breakdown</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {/* Direct Personal Balance Row */}
                <div className="flex items-center justify-between p-3 bg-indigo-50/40 border border-indigo-100/60 rounded-xl transition-all hover:bg-indigo-50/80">
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-xl bg-indigo-100 flex items-center justify-center text-indigo-600 shadow-sm shrink-0">
                      <Globe size={18} />
                    </div>
                    <div>
                      <span className="font-semibold text-foreground block text-xs tracking-tight">
                        Direct Balance
                      </span>
                      <span className="text-[10px] text-muted-foreground font-medium">
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
                  <div className="pt-2 border-t border-border space-y-2">
                    <p className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider px-1">Group Splits</p>
                    {sharedGroups.map((group) => {
                      const bal = groupBalancesMap[group.id] || 0;
                      return (
                        <div key={group.id} className="flex items-center justify-between p-2.5 hover:bg-muted/80 rounded-lg transition-colors">
                          <span className="text-xs font-semibold text-foreground truncate max-w-[130px]" title={group.name}>
                            {group.name}
                          </span>
                          <span className={`text-xs font-bold ${
                            bal > 0 ? "text-emerald-600 dark:text-emerald-400" : bal < 0 ? "text-rose-600 dark:text-rose-400" : "text-muted-foreground"
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
                <div className="flex items-center gap-2 text-muted-foreground">
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
                      <Card key={group.id} className="hover:bg-muted/50 transition-colors border-border shadow-sm">
                        <Link to={`/groups/${group.id}`}>
                          <CardContent className="flex items-center justify-between p-3">
                            <div className="flex items-center gap-3">
                              <div className="w-10 h-10 rounded-full bg-orange-100 dark:bg-orange-900/30 flex items-center justify-center text-orange-600 dark:text-orange-400">
                                <Users size={20} />
                              </div>
                              <span className="font-medium text-foreground">
                                {group.name}
                              </span>
                            </div>
                          </CardContent>
                        </Link>
                      </Card>
                    ))}
                  </div>
                ) : (
                  <p className="text-muted-foreground italic">
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
                      const expense = activity.data;
                      return (
                        <Card key={`expense-${expense.id}-${index}`} className="border-border shadow-sm">
                          <CardContent className="flex items-center justify-between p-3">
                            <div className="flex items-center gap-3">
                              <div className="w-10 h-10 rounded-full bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center text-blue-600 dark:text-blue-400">
                                <Receipt size={20} />
                              </div>
                              <div>
                                <p className="font-medium text-foreground">
                                  {expense.description}
                                </p>
                                <p className="text-xs text-muted-foreground">
                                  {new Date(
                                    expense.expenseDate,
                                  ).toLocaleDateString()}
                                </p>
                              </div>
                            </div>
                            <div className="text-right">
                              <p className="font-bold text-foreground">
                                {expense.currency} {expense.amount.toFixed(2)}
                              </p>
                              <p className="text-xs text-muted-foreground">
                                Paid by{" "}
                                {expense.paidBy === friendId
                                  ? friend.firstName
                                  : "You"}
                              </p>
                            </div>
                          </CardContent>
                        </Card>
                      );
                    } else {
                      const settlement = activity.data;
                      const isPayer = settlement.payerId === Number(currentUser?.id);
                      const isEditing = editingSettlementId === settlement.id;
                      const canEdit = settlement.status !== 'COMPLETED';

                      let badgeClassName = "bg-gray-100 text-gray-800 hover:bg-gray-100/80 border-gray-200";
                      if (settlement.status === 'COMPLETED') {
                        badgeClassName = "bg-green-100 text-green-800 border-green-200 hover:bg-green-100/80";
                      } else if (settlement.status === 'MARKED_PAID') {
                        badgeClassName = "bg-yellow-100 text-yellow-800 border-yellow-200 hover:bg-yellow-100/80";
                      }

                      const isGlobalPayment = isGlobalPaymentFor(settlement);

                      return (
                        <Card key={`settlement-${settlement.id}-${index}`} className="shadow-sm hover:shadow-md transition-shadow border-border">
                          <CardContent className="p-3">
                            <div className="flex items-center justify-between">
                              <div className="flex items-center gap-3">
                                <div className="w-10 h-10 rounded-full bg-green-100 dark:bg-green-900/30 flex items-center justify-center text-green-600 dark:text-green-400">
                                  <DollarSign size={20} />
                                </div>
                                <div className="flex-1">
                                  <div className="flex items-center gap-2 flex-wrap">
                                    <p className="font-medium text-foreground">
                                      {isPayer ? `You paid ${friend.firstName}` : `${friend.firstName} paid you`}
                                    </p>
                                    <Badge className={badgeClassName}>
                                      {settlement.status === 'MARKED_PAID' ? 'Pending Confirmation' :
                                       settlement.status === 'COMPLETED' ? 'Settled' : 'Pending'}
                                    </Badge>
                                    {isGlobalPayment && (
                                      <span className="px-2 py-0.5 text-[9px] font-bold rounded bg-indigo-50 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-800 flex items-center gap-1 shadow-sm uppercase tracking-wider">
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
                                        className="p-1 text-muted-foreground hover:text-blue-600 transition-colors rounded"
                                        title="Edit payment"
                                      >
                                        <Pencil size={14} />
                                      </button>
                                    )}
                                  </div>
                                  <p className="text-xs text-muted-foreground">
                                    {new Date(settlement.createdAt).toLocaleDateString()}{" "}
                                    {new Date(settlement.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                  </p>
                                </div>
                              </div>
                              <div className="text-right">
                                {isEditing ? (
                                  <div className="flex items-center gap-1">
                                    <span className="text-muted-foreground">$</span>
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
                                  <p className="font-bold text-foreground">
                                    ${settlement.amount.toFixed(2)}
                                  </p>
                                )}
                              </div>
                            </div>

                            {/* Group Allocations */}
                            {settlement.allocations && settlement.allocations.length > 0 ? (
                              <div className="mt-2 ml-13 pl-3 border-l-2 border-border space-y-1">
                                {settlement.allocations.map((alloc, aIdx) => (
                                  <div key={aIdx} className="flex items-center justify-between text-xs text-muted-foreground py-0.5">
                                    <span className="flex items-center gap-1.5">
                                      {alloc.groupId ? (
                                        <>
                                          <Users size={10} className="text-muted-foreground" />
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
                                    <span className="font-medium text-foreground">
                                      ${alloc.amount.toFixed(2)}
                                    </span>
                                  </div>
                                ))}
                              </div>
                            ) : (
                              /* Entirely Direct Payment indicator when no allocations array exists */
                              <div className="mt-2 ml-13 pl-3 border-l-2 border-border">
                                <div className="flex items-center justify-between text-xs text-muted-foreground py-0.5">
                                  <span className="flex items-center gap-1.5">
                                    <Globe size={10} className="text-indigo-500" />
                                    <span className="text-indigo-600 dark:text-indigo-400 font-semibold bg-indigo-50 dark:bg-indigo-900/30 px-1.5 py-0.5 rounded text-[10px]">
                                      Direct Personal Balance
                                    </span>
                                  </span>
                                  <span className="font-medium text-foreground">
                                    ${settlement.amount.toFixed(2)}
                                  </span>
                                </div>
                              </div>
                            )}
                          </CardContent>
                        </Card>
                      );
                    }
                  })}
                </div>
              ) : (
                <p className="text-muted-foreground italic">
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

      {friend && (
        <>
          <Dialog open={isAddFriendModalOpen} onOpenChange={(open) => { if (!open) setIsAddFriendModalOpen(false); }}>
            <DialogContent className="sm:max-w-[425px]">
              <DialogHeader>
                <DialogTitle>Add Friend</DialogTitle>
                <DialogDescription>
                  Send a friend request to {friend.firstName} {friend.lastName}?
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="outline" onClick={() => setIsAddFriendModalOpen(false)}>Cancel</Button>
                <Button onClick={() => addFriendMutation.mutate()} disabled={addFriendMutation.isPending}>
                  {addFriendMutation.isPending ? "Sending..." : "Send Request"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>

          <Dialog open={isCancelRequestModalOpen} onOpenChange={(open) => { if (!open) setIsCancelRequestModalOpen(false); }}>
            <DialogContent className="sm:max-w-[425px]">
              <DialogHeader>
                <DialogTitle>Cancel Friend Request</DialogTitle>
                <DialogDescription>
                  Are you sure you want to cancel the friend request sent to {friend.firstName} {friend.lastName}?
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="outline" onClick={() => setIsCancelRequestModalOpen(false)}>Keep Request</Button>
                <Button variant="destructive" onClick={() => cancelRequestMutation.mutate()} disabled={cancelRequestMutation.isPending}>
                  {cancelRequestMutation.isPending ? "Cancelling..." : "Cancel Request"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </>
      )}
    </DashboardLayout>
  );
};

export default FriendDetailPage;
