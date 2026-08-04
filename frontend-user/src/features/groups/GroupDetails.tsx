import { useState, useMemo } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import axios from "axios";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { groupService } from "./groupService";
import { friendService } from "../users/friendService";
import { useAuthStore } from "../../store/authStore";
import DashboardLayout from "../../components/layout/DashboardLayout";
import { useDisplayNames } from "../../hooks/useDisplayName";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { toast } from "sonner";
import AddMemberModal from "./AddMemberModal";
import ExpenseModal from "../expenses/ExpenseModal";
import type { Expense } from "../../types/expense";
import GroupBalances from "../balances/GroupBalances";
import GroupActivity from "./GroupActivity";
import { balanceInGroup } from "../balances/ledger";
import { queryKeys, invalidations, bindInvalidations } from "../../lib/queryKeys";
import {
  selfRole,
  canLeaveGroup,
  hasPendingSettlements,
  canManageMembers,
} from "./membershipGating";
import { settlementService } from "../balances/settlementService";
import { expenseService } from "../expenses/expenseService";
import { categoryService } from "../expenses/categoryService";
import {
  Loader2,
  ArrowLeft,
  LogOut,
  Users,
  MoreVertical,
  ShieldAlert,
  Settings,
  UserPlus,
  Activity,
  DollarSign,
  Plus,
  Receipt,
  Trash2,
  Calendar,
} from "lucide-react";

const GroupDetails = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // The minimal `invalidateQueries` surface the queryKeys invalidations expect.
  const invalidate = bindInvalidations(queryClient);
  const { user } = useAuthStore();

  const [isLeaveModalOpen, setIsLeaveModalOpen] = useState(false);
  const [isSelfDemoteModalOpen, setIsSelfDemoteModalOpen] = useState(false);
  const [isAddMemberModalOpen, setIsAddMemberModalOpen] = useState(false);
  const [isExpenseModalOpen, setIsExpenseModalOpen] = useState(false);
  const [editingExpense, setEditingExpense] = useState<Expense | undefined>(
    undefined,
  );
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [expenseToDelete, setExpenseToDelete] = useState<Expense | null>(null);
  const [activeTab, setActiveTab] = useState<
    "expenses" | "members" | "balances"
  >("expenses");

  const { data: group, isLoading } = useQuery({
    queryKey: queryKeys.group(Number(id)),
    queryFn: () => groupService.getGroup(Number(id)),
    enabled: !!id,
  });

  const { data: balancesResponse, isLoading: isBalancesLoading } = useQuery({
    queryKey: queryKeys.groupBalances(Number(id)),
    queryFn: () => groupService.getBalances(Number(id)),
    enabled: !!id,
  });

  const { data: friends, isLoading: isFriendsLoading } = useQuery({
    queryKey: queryKeys.friends(Number(user?.id)),
    queryFn: () => friendService.getFriends(Number(user?.id)),
    enabled: !!user?.id,
  });

  const { data: settlements, isLoading: isSettlementsLoading } = useQuery({
    queryKey: queryKeys.groupSettlements(Number(id)),
    queryFn: () => settlementService.getSettlementsByGroup(Number(id)),
    enabled: !!id,
  });

  const { data: expenses, isLoading: isExpensesLoading } = useQuery({
    queryKey: queryKeys.expenses(Number(id)),
    queryFn: () => expenseService.getGroupExpenses(Number(id)),
    enabled: !!id,
  });

  const { data: categories } = useQuery({
    queryKey: queryKeys.categories(),
    queryFn: categoryService.getCategories,
  });

  const sortedExpenses = useMemo(() => {
    if (!expenses) return [];
    return [...expenses].sort((a, b) => {
      const dateA = new Date(a.expenseDate).getTime();
      const dateB = new Date(b.expenseDate).getTime();
      if (dateA !== dateB) {
        return dateB - dateA;
      }
      return b.id - a.id;
    });
  }, [expenses]);

  // Derive member names via the shared display-name hook
  const memberIds = useMemo(
    () => group?.members.map((m) => m.userId) ?? [],
    [group],
  );
  const memberNames = useDisplayNames(memberIds);

  const handleAddExpense = () => {
    setEditingExpense(undefined);
    setIsExpenseModalOpen(true);
  };

  const handleEditExpense = (expense: Expense) => {
    setEditingExpense(expense);
    setIsExpenseModalOpen(true);
  };

  const canManageExpense = (expense: Expense) => {
    if (!group) return false;
    const member = group.members.find((m) => m.userId === Number(user?.id));
    if (!member) return false;

    const isAdmin = member.role === "ADMIN" || group.createdBy === Number(user?.id);
    const isPayer = expense.paidBy === Number(user?.id);

    return isAdmin || isPayer || group.allowMembersToEditExpenses;
  };

  const deleteExpenseMutation = useMutation({
    mutationFn: (expenseId: number) =>
      expenseService.deleteExpense(Number(id), expenseId),
    onSuccess: () => {
      invalidations.expenseMutated(invalidate, Number(id));
      toast.success("Expense deleted successfully");
      setIsDeleteModalOpen(false);
      setExpenseToDelete(null);
    },
    onError: () => {
      toast.error("Failed to delete expense");
    },
  });

  const handleDeleteExpenseClick = (expense: Expense) => {
    setExpenseToDelete(expense);
    setIsDeleteModalOpen(true);
  };

  const confirmDeleteExpense = () => {
    if (expenseToDelete) {
      deleteExpenseMutation.mutate(expenseToDelete.id);
    }
  };

  const leaveMutation = useMutation({
    mutationFn: () => groupService.removeMember(Number(id), Number(user?.id)),
    onSuccess: () => {
      invalidate(queryKeys.groups());
      toast.success("Left group successfully");
      navigate("/groups");
    },
    onError: () => {
      toast.error("Failed to leave group");
    },
  });

  const updateRoleMutation = useMutation({
    mutationFn: ({
      userId,
      role,
    }: {
      userId: number;
      role: "ADMIN" | "MEMBER";
    }) => groupService.updateMemberRole(Number(id), userId, role),
    onSuccess: (_, variables) => {
      invalidate(queryKeys.group(Number(id)));
      toast.success("Role updated successfully");
      if (
        variables.userId === Number(user?.id) &&
        variables.role === "MEMBER"
      ) {
        setIsSelfDemoteModalOpen(false);
      }
    },
    onError: (error) => {
      let message = "Failed to update role";
      if (axios.isAxiosError(error)) {
        message = error.response?.data?.message || message;
      }
      toast.error(message);
      setIsSelfDemoteModalOpen(false);
    },
  });

  const updateGroupMutation = useMutation({
    mutationFn: (data: Partial<import("../../types/group").Group>) =>
      groupService.updateGroup(Number(id), data),
    onSuccess: () => {
      invalidate(queryKeys.group(Number(id)));
      toast.success("Group settings updated");
    },
    onError: () => {
      toast.error("Failed to update group settings");
    },
  });

  const handleLeave = () => {
    leaveMutation.mutate();
  };

  const handleRoleUpdate = (userId: number, newRole: "ADMIN" | "MEMBER") => {
    if (userId === Number(user?.id) && newRole === "MEMBER") {
      setIsSelfDemoteModalOpen(true);
      return;
    }
    updateRoleMutation.mutate({ userId, role: newRole });
  };

  const confirmSelfDemote = () => {
    updateRoleMutation.mutate({ userId: Number(user?.id), role: "MEMBER" });
  };

  const currentUserBalance = balanceInGroup(
    balancesResponse?.balances ?? [],
    Number(user?.id),
  );

  // Role + the Settled Membership Invariant (CONTEXT.md), from one module
  // instead of being re-derived inline in the view.
  const currentUserRole = selfRole(group, Number(user?.id));
  const hasPending = hasPendingSettlements(settlements, Number(user?.id));
  const canLeave = canLeaveGroup({
    balance: currentUserBalance,
    settlements,
    currentUserId: Number(user?.id),
  });

  if (isLoading || isFriendsLoading) {
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
          <h2 className="text-xl font-bold text-foreground">Group not found</h2>
          <Button onClick={() => navigate("/groups")} className="mt-4">
            Back to Groups
          </Button>
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout breadcrumbs={[{ label: "Groups", href: "/groups" }, { label: group.name }]}>
      <div className="flex flex-col h-[calc(100vh-112px)] overflow-hidden space-y-4 pb-2">
        <div className="flex items-center gap-4 shrink-0">
          <Button variant="ghost" size="sm" onClick={() => navigate("/groups")}>
            <ArrowLeft size={20} />
          </Button>
          <div>
            <h1 className="text-2xl font-bold text-foreground">{group.name}</h1>
            {group.description && (
              <p className="text-muted-foreground">{group.description}</p>
            )}
          </div>
        </div>

        {/* Tab Navigation & Content managed by Shadcn Tabs */}
        <Tabs
          value={activeTab}
          onValueChange={(val) => setActiveTab(val as "expenses" | "members" | "balances")}
          className="w-full flex-1 flex flex-col min-h-0"
        >
          <TabsList variant="line" className="border-b border-border shrink-0 w-full justify-start gap-8 bg-transparent p-0 rounded-none h-auto -mb-px">
            <TabsTrigger
              value="expenses"
              className="py-4 px-1 border-b-2 bg-transparent rounded-none border-transparent text-muted-foreground hover:text-foreground hover:border-gray-300 data-[active]:border-blue-500 data-[active]:text-blue-600"
            >
              <div className="flex items-center gap-2">
                <Receipt size={18} />
                <span>Expenses</span>
              </div>
            </TabsTrigger>
            <TabsTrigger
              value="members"
              className="py-4 px-1 border-b-2 bg-transparent rounded-none border-transparent text-muted-foreground hover:text-foreground hover:border-gray-300 data-[active]:border-blue-500 data-[active]:text-blue-600"
            >
              <div className="flex items-center gap-2">
                <Users size={18} />
                <span>Members</span>
              </div>
            </TabsTrigger>
            <TabsTrigger
              value="balances"
              className="py-4 px-1 border-b-2 bg-transparent rounded-none border-transparent text-muted-foreground hover:text-foreground hover:border-gray-300 data-[active]:border-blue-500 data-[active]:text-blue-600"
            >
              <div className="flex items-center gap-2">
                <DollarSign size={18} />
                <span>Balances</span>
              </div>
            </TabsTrigger>
          </TabsList>

          {/* Balance Summary Card */}
          {!isBalancesLoading && currentUserBalance !== 0 && (
            <Card className="shrink-0 my-4 shadow-sm border-border">
              <CardContent className="p-4 flex items-center justify-between">
                <div className="flex items-center gap-4">
                  <div
                    className={`p-3 rounded-full flex items-center justify-center ${
                      currentUserBalance > 0
                        ? "bg-emerald-500/10 text-emerald-500"
                        : "bg-destructive/10 text-destructive"
                    }`}
                  >
                    <DollarSign size={24} />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-muted-foreground">
                      Your Group Balance
                    </p>
                    <h3
                      className={`text-xl font-bold ${
                        currentUserBalance > 0
                          ? "text-emerald-500"
                          : "text-destructive"
                      }`}
                    >
                      {currentUserBalance > 0 ? "You are owed" : "You owe"} ${Math.abs(currentUserBalance).toFixed(2)}
                    </h3>
                  </div>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setActiveTab("balances")}
                  className={
                    currentUserBalance > 0
                      ? "text-emerald-500 border-emerald-500/30 hover:bg-emerald-500/10 hover:text-emerald-600"
                      : "text-destructive border-destructive/30 hover:bg-destructive/10 hover:text-destructive"
                  }
                >
                  View Details
                </Button>
              </CardContent>
            </Card>
          )}

          <div className="grid grid-cols-1 lg:grid-cols-4 gap-6 flex-1 min-h-0 pt-4">
            <div className="lg:col-span-3 h-full flex flex-col min-h-0">
              
              {/* EXPENSES TAB CONTENT */}
              <TabsContent value="expenses" className="h-full flex flex-col min-h-0 space-y-4">
                <div className="flex justify-between items-center px-1 shrink-0">
                  <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                    <Receipt size={20} className="text-blue-600" />
                    <span>Group Expenses</span>
                  </h2>
                  <Button
                    size="sm"
                    onClick={() => handleAddExpense()}
                    className="flex items-center gap-2"
                  >
                    <Plus size={18} />
                    <span>Add Expense</span>
                  </Button>
                </div>
                
                {isExpensesLoading ? (
                  <div className="flex justify-center p-8" data-testid="loader">
                    <Loader2 className="animate-spin text-blue-600" size={24} />
                  </div>
                ) : !expenses || expenses.length === 0 ? (
                  <Card>
                    <CardContent className="py-12 text-center">
                      <Receipt className="mx-auto text-gray-300 mb-4" size={48} />
                      <h3 className="text-lg font-medium text-foreground mb-1">
                        No expenses yet
                      </h3>
                      <p className="text-muted-foreground italic mb-6">
                        Add an expense to get started splitting with the group!
                      </p>
                      <Button
                        onClick={() => handleAddExpense()}
                        className="flex items-center gap-2 mx-auto"
                      >
                        <Plus size={18} />
                        <span>Add Expense</span>
                      </Button>
                    </CardContent>
                  </Card>
                ) : (
                  <ScrollArea className="h-[500px] pr-4">
                    <div className="space-y-3">
                      {sortedExpenses.map((expense) => {
                      const isPayer = expense.paidBy === Number(user?.id);
                      const payerName = isPayer ? "You" : (memberNames[expense.paidBy] ?? `User ${expense.paidBy}`);
                      const date = new Date(expense.expenseDate);
                      const mySplit = expense.splits.find((s) => s.userId === Number(user?.id));
                      const canManage = canManageExpense(expense);

                      const category = categories?.find((c) => c.id === expense.categoryId);
                      const categoryName = category?.name ?? "General";

                      let balanceIndicator = null;
                      if (isPayer) {
                        const lentAmount = expense.splits
                          .filter((s) => s.userId !== Number(user?.id))
                          .reduce((sum, s) => sum + s.shareAmount, 0);
                        if (lentAmount > 0) {
                          balanceIndicator = (
                            <span className="text-green-600 font-semibold text-sm">
                              you lent <span className="font-bold">${lentAmount.toFixed(2)}</span>
                            </span>
                          );
                        } else {
                          balanceIndicator = <span className="text-muted-foreground text-sm">you paid for yourself</span>;
                        }
                      } else {
                        if (mySplit) {
                          balanceIndicator = (
                            <span className="text-red-500 font-semibold text-sm">
                              you owe {payerName} <span className="font-bold">${mySplit.shareAmount.toFixed(2)}</span>
                            </span>
                          );
                        } else {
                          balanceIndicator = <span className="text-muted-foreground text-sm">not involved</span>;
                        }
                      }

                      return (
                        <Card key={`expense-${expense.id}`} className="hover:shadow-md transition-shadow">
                          <CardContent className="p-4 flex items-center justify-between">
                            <div className="flex items-center gap-4 flex-1 min-w-0">
                              <div className="w-10 h-10 rounded-full bg-blue-50 flex items-center justify-center text-blue-600 shrink-0">
                                <Receipt size={20} />
                              </div>
                              <div className="flex-1 min-w-0">
                                <h3 className="font-semibold text-foreground truncate">{expense.description}</h3>
                                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-0.5 text-xs text-muted-foreground">
                                  <span>Paid by <span className="font-medium text-gray-700">{payerName}</span></span>
                                  <span>•</span>
                                  <span className="flex items-center gap-1">
                                    <Calendar size={12} />
                                    {date.toLocaleDateString()}
                                  </span>
                                  <span>•</span>
                                  <Badge className="bg-muted text-foreground border-border hover:bg-muted/80">{categoryName}</Badge>
                                </div>
                              </div>
                            </div>

                            <div className="flex items-center gap-6 shrink-0">
                              <div className="text-right">
                                <div className="text-base font-bold text-foreground">${expense.amount.toFixed(2)}</div>
                                <div>{balanceIndicator}</div>
                              </div>
                              
                              {canManage && (
                                <DropdownMenu>
                                  <DropdownMenuTrigger
                                    className="p-1 hover:bg-muted rounded-full text-muted-foreground hover:text-muted-foreground transition-colors"
                                    aria-label={`Actions for ${expense.description}`}
                                  >
                                    <MoreVertical size={20} />
                                  </DropdownMenuTrigger>
                                  <DropdownMenuContent align="end">
                                    <DropdownMenuItem onClick={() => handleEditExpense(expense)}>
                                      Edit
                                    </DropdownMenuItem>
                                    <DropdownMenuItem
                                      variant="destructive"
                                      onClick={() => handleDeleteExpenseClick(expense)}
                                    >
                                      Delete
                                    </DropdownMenuItem>
                                  </DropdownMenuContent>
                                </DropdownMenu>
                              )}
                            </div>
                          </CardContent>
                        </Card>
                      );
                    })}
                    </div>
                  </ScrollArea>
                )}
              </TabsContent>

              {/* MEMBERS TAB CONTENT */}
              <TabsContent value="members" className="h-full overflow-y-auto pr-1 space-y-4 pb-2">
                <Card>
                  <CardHeader className="flex flex-row items-center justify-between space-y-0">
                    <CardTitle className="flex items-center gap-2">
                      <Users size={20} />
                      <span>Members</span>
                    </CardTitle>
                    <div className="flex items-center gap-2">
                      <span className="text-sm text-muted-foreground">
                        {group.members.length} members
                      </span>
                      {canManageMembers(
                        currentUserRole,
                        group.allowMembersToManageMembers,
                      ) && (
                        <button
                          aria-label="Add member"
                          onClick={() => setIsAddMemberModalOpen(true)}
                          className="p-1.5 text-blue-600 hover:text-blue-700 hover:bg-blue-50 rounded-md transition-colors"
                          title="Add Member"
                        >
                          <UserPlus size={16} />
                        </button>
                      )}
                    </div>
                  </CardHeader>
                  <CardContent>
                    <div className="divide-y divide-gray-100">
                      {group.members.map((member) => {
                        const displayName =
                          memberNames[member.userId] ?? `User ${member.userId}`;

                        let badgeClassName = "bg-muted text-foreground border-border hover:bg-muted/80";
                        let roleLabel = "Member";

                        if (member.userId === group.createdBy) {
                          badgeClassName = "bg-purple-100 text-purple-800 border-purple-200 hover:bg-purple-100/80";
                          roleLabel = "Owner";
                        } else if (member.role === "ADMIN") {
                          badgeClassName = "bg-blue-100 text-blue-800 border-blue-200 hover:bg-blue-100/80";
                          roleLabel = "Admin";
                        }

                        const isCurrentUser = member.userId === Number(user?.id);
                        const isFriend = friends?.some(
                          (f) => f.id === member.userId,
                        );
                        const isTempFriend =
                          !isCurrentUser && friends && !isFriend;

                        const debtToMember = balancesResponse?.simplifiedDebts.find(
                          (d) => d.from === Number(user?.id) && d.to === member.userId,
                        );
                        const debtFromMember = balancesResponse?.simplifiedDebts.find(
                          (d) => d.to === Number(user?.id) && d.from === member.userId,
                        );

                        const memberInfoContent = (
                          <>
                            <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center text-blue-700 font-medium text-sm shrink-0">
                              {displayName.charAt(0)}
                            </div>
                            <div className="flex flex-col text-left">
                              <span className="text-sm font-medium text-foreground group-hover:text-blue-600 transition-colors">
                                {displayName}
                              </span>
                              {!isCurrentUser && (
                                <span className="text-xs">
                                  {isBalancesLoading ? (
                                    <span className="text-muted-foreground font-normal animate-pulse">
                                      loading balance...
                                    </span>
                                  ) : debtToMember ? (
                                    <span className="text-red-500 font-medium">
                                      you owe ${debtToMember.amount.toFixed(2)}
                                    </span>
                                  ) : debtFromMember ? (
                                    <span className="text-green-600 font-medium">
                                      owes you ${debtFromMember.amount.toFixed(2)}
                                    </span>
                                  ) : (
                                    <span className="text-muted-foreground font-normal">
                                      settled up
                                    </span>
                                  )}
                                </span>
                              )}
                            </div>
                          </>
                        );

                        return (
                          <div
                            key={member.id}
                            className="flex items-center justify-between py-3 first:pt-0 last:pb-0"
                          >
                            {isCurrentUser ? (
                              <div className="flex items-center gap-3">
                                {memberInfoContent}
                              </div>
                            ) : (
                              <Link
                                to={`/friends/${member.userId}`}
                                className="flex items-center gap-3 hover:opacity-80 transition-opacity group cursor-pointer"
                              >
                                {memberInfoContent}
                              </Link>
                            )}
                            <div className="flex items-center gap-2">
                              {isTempFriend && (
                                <Badge className="bg-orange-100 text-orange-800 border-orange-200 hover:bg-orange-100/80">Temp Friend</Badge>
                              )}
                              <Badge className={badgeClassName}>{roleLabel}</Badge>

                              {/* Role Management Dropdown */}
                              {(currentUserRole === "ADMIN" ||
                                currentUserRole === "OWNER") &&
                                member.userId !== group.createdBy && (
                                  <DropdownMenu>
                                    <DropdownMenuTrigger
                                      className="p-1 text-muted-foreground hover:text-muted-foreground rounded-full hover:bg-muted"
                                      aria-label="Manage role"
                                    >
                                      <MoreVertical size={16} />
                                    </DropdownMenuTrigger>
                                    <DropdownMenuContent align="end">
                                      <DropdownMenuItem
                                        disabled={updateRoleMutation.isPending}
                                        onClick={() =>
                                          handleRoleUpdate(
                                            member.userId,
                                            member.role === "ADMIN"
                                              ? "MEMBER"
                                              : "ADMIN",
                                          )
                                        }
                                      >
                                        {member.role === "ADMIN"
                                          ? "Demote to Member"
                                          : "Promote to Admin"}
                                      </DropdownMenuItem>
                                    </DropdownMenuContent>
                                  </DropdownMenu>
                                )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </CardContent>
                </Card>

                {/* Relocated Group Settings */}
                {currentUserRole === "OWNER" && (
                  <Card>
                    <CardHeader>
                      <CardTitle className="flex items-center gap-2">
                        <Settings size={20} />
                        <span>Group Settings</span>
                      </CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="space-y-4">
                        <div className="flex items-center justify-between">
                          <div className="flex flex-col">
                            <span className="text-sm font-medium text-foreground">
                              Manage Members
                            </span>
                            <span className="text-xs text-muted-foreground">
                              Allow members to add/remove others
                            </span>
                          </div>
                          <Switch
                            checked={group.allowMembersToManageMembers}
                            onCheckedChange={(checked) =>
                              updateGroupMutation.mutate({
                                allowMembersToManageMembers: checked,
                              })
                            }
                            disabled={updateGroupMutation.isPending}
                            aria-label="Toggle allow members to manage members"
                          />
                        </div>

                        <div className="flex items-center justify-between">
                          <div className="flex flex-col">
                            <span className="text-sm font-medium text-foreground">
                              Collaborative Editing
                            </span>
                            <span className="text-xs text-muted-foreground">
                              Allow members to edit/delete expenses
                            </span>
                          </div>
                          <Switch
                            checked={group.allowMembersToEditExpenses}
                            onCheckedChange={(checked) =>
                              updateGroupMutation.mutate({
                                allowMembersToEditExpenses: checked,
                              })
                            }
                            disabled={updateGroupMutation.isPending}
                            aria-label="Toggle allow members to edit expenses"
                          />
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                )}

                {/* Relocated Actions */}
                <Card>
                  <CardHeader>
                    <CardTitle>Actions</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <Button
                      variant="outline"
                      onClick={() => setIsLeaveModalOpen(true)}
                      className="w-full flex items-center justify-center gap-2 text-red-600 border-red-200 hover:bg-red-50 hover:text-red-700"
                    >
                      <LogOut size={18} />
                      <span>Leave Group</span>
                    </Button>
                  </CardContent>
                </Card>
              </TabsContent>

              {/* BALANCES TAB CONTENT */}
              <TabsContent value="balances" className="h-full overflow-y-auto pr-1">
                <GroupBalances groupId={Number(id)} />
              </TabsContent>
            </div>

            {/* RIGHT SIDEBAR PANEL: ALWAYS-ON SHARED ACTIVITY (25%) */}
            <div className="lg:col-span-1 h-full flex flex-col min-h-0 space-y-4">
              <div className="px-1 flex items-center justify-between shrink-0">
                <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                  <Activity size={20} className="text-blue-600" />
                  <span>Shared Activity</span>
                </h2>
              </div>
              <div className="flex-1 overflow-y-auto pr-1">
                <GroupActivity
                  groupId={Number(id)}
                  onEditExpense={handleEditExpense}
                  group={group}
                />
              </div>
            </div>
          </div>
        </Tabs>
      </div>

      <Dialog open={isLeaveModalOpen} onOpenChange={(open) => { if (!open) setIsLeaveModalOpen(false); }}>
        <DialogContent className="max-w-lg bg-white">
          <DialogHeader className="border-b border-border pb-3">
            <DialogTitle className="text-xl font-semibold text-foreground">Leave Group</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 pt-2">
            {isBalancesLoading || isSettlementsLoading ? (
              <div className="flex justify-center py-4">
                <Loader2 className="animate-spin text-blue-600" size={24} />
              </div>
            ) : !canLeave ? (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                {hasPending ? (
                  "You cannot leave this group while you have pending unconfirmed payments."
                ) : (
                  `You cannot leave this group while you have an outstanding balance (${currentUserBalance}).`
                )}
              </div>
            ) : (
              <p className="text-muted-foreground">
                Are you sure you want to leave this group? You will no longer be
                able to see expenses or add new ones.
              </p>
            )}
            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="outline"
                onClick={() => setIsLeaveModalOpen(false)}
                disabled={leaveMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="destructive"
                onClick={handleLeave}
                disabled={
                  leaveMutation.isPending || isBalancesLoading || isSettlementsLoading || !canLeave
                }
              >
                {leaveMutation.isPending ? "Leaving..." : "Leave Group"}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={isSelfDemoteModalOpen} onOpenChange={(open) => { if (!open) setIsSelfDemoteModalOpen(false); }}>
        <DialogContent className="max-w-lg bg-white">
          <DialogHeader className="border-b border-border pb-3">
            <DialogTitle className="text-xl font-semibold text-foreground">Confirm Self-Demotion</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 pt-2">
            <div className="flex items-start gap-3 p-3 bg-amber-50 border border-amber-200 rounded-lg text-amber-800 text-sm">
              <ShieldAlert className="shrink-0" size={20} />
              <p>
                Are you sure you want to demote yourself to a Member? You will
                lose all administrative privileges in this group.
              </p>
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="outline"
                onClick={() => setIsSelfDemoteModalOpen(false)}
                disabled={updateRoleMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="default"
                onClick={confirmSelfDemote}
                disabled={updateRoleMutation.isPending}
              >
                {updateRoleMutation.isPending
                  ? "Updating..."
                  : "Confirm Demotion"}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={isDeleteModalOpen} onOpenChange={(open) => { if (!open) setIsDeleteModalOpen(false); }}>
        <DialogContent className="max-w-lg bg-white">
          <DialogHeader className="border-b border-border pb-3">
            <DialogTitle className="text-xl font-semibold text-foreground">Delete Expense</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 pt-2">
            <p className="text-muted-foreground">
              Are you sure you want to delete "
              <span className="font-semibold text-foreground">
                {expenseToDelete?.description}
              </span>
              "? This action cannot be undone and will update everyone's balances.
            </p>
            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="outline"
                onClick={() => setIsDeleteModalOpen(false)}
                disabled={deleteExpenseMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="destructive"
                onClick={confirmDeleteExpense}
                disabled={deleteExpenseMutation.isPending}
                className="flex items-center gap-2"
              >
                {deleteExpenseMutation.isPending ? (
                  <>
                    <Loader2 size={18} className="animate-spin" />
                    <span>Deleting...</span>
                  </>
                ) : (
                  <>
                    <Trash2 size={18} />
                    <span>Delete Expense</span>
                  </>
                )}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {group && (
        <AddMemberModal
          isOpen={isAddMemberModalOpen}
          onClose={() => setIsAddMemberModalOpen(false)}
          group={group}
        />
      )}

      {group && isExpenseModalOpen && (
        <ExpenseModal
          key={editingExpense?.id || "new"}
          isOpen={isExpenseModalOpen}
          onClose={() => {
            setIsExpenseModalOpen(false);
            setEditingExpense(undefined);
          }}
          group={group}
          expense={editingExpense}
        />
      )}
    </DashboardLayout>
  );
};

export default GroupDetails;
