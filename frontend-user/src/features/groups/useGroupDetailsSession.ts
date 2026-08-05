import { useState, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import axios from "axios";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { groupService } from "./groupService";
import { friendService } from "../users/friendService";
import { useAuthStore } from "../../store/authStore";
import { useDisplayNames } from "../../hooks/useDisplayName";
import { toast } from "sonner";
import type { Expense } from "../../types/expense";
import { balanceInGroup } from "../balances/ledger";
import { queryKeys, invalidations, bindInvalidations } from "../../lib/queryKeys";
import { useGroupGovernance } from "./membershipGating";
import { settlementService } from "../balances/settlementService";
import { expenseService } from "../expenses/expenseService";
import { categoryService } from "../expenses/categoryService";

export type TabType = "expenses" | "members" | "balances";

export function useGroupDetailsSession() {
  const { id } = useParams<{ id: string }>();
  const groupId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

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
  const [activeTab, setActiveTab] = useState<TabType>("expenses");

  const { data: group, isLoading: isGroupLoading } = useQuery({
    queryKey: queryKeys.group(groupId),
    queryFn: () => groupService.getGroup(groupId),
    enabled: !!id,
  });

  const { data: balancesResponse, isLoading: isBalancesLoading } = useQuery({
    queryKey: queryKeys.groupBalances(groupId),
    queryFn: () => groupService.getBalances(groupId),
    enabled: !!id,
  });

  const { data: friends, isLoading: isFriendsLoading } = useQuery({
    queryKey: queryKeys.friends(Number(user?.id)),
    queryFn: () => friendService.getFriends(Number(user?.id)),
    enabled: !!user?.id,
  });

  const { data: settlements, isLoading: isSettlementsLoading } = useQuery({
    queryKey: queryKeys.groupSettlements(groupId),
    queryFn: () => settlementService.getSettlementsByGroup(groupId),
    enabled: !!id,
  });

  const { data: expenses, isLoading: isExpensesLoading } = useQuery({
    queryKey: queryKeys.expenses(groupId),
    queryFn: () => expenseService.getGroupExpenses(groupId),
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

  const memberIds = useMemo(
    () => group?.members.map((m) => m.userId) ?? [],
    [group],
  );
  const memberNames = useDisplayNames(memberIds);

  const currentUserBalance = balanceInGroup(
    balancesResponse?.balances ?? [],
    Number(user?.id),
  );

  const governance = useGroupGovernance({
    group,
    currentUserId: Number(user?.id),
    currentUserBalance,
    settlements,
  });

  const handleAddExpense = () => {
    setEditingExpense(undefined);
    setIsExpenseModalOpen(true);
  };

  const handleEditExpense = (expense: Expense) => {
    setEditingExpense(expense);
    setIsExpenseModalOpen(true);
  };

  const deleteExpenseMutation = useMutation({
    mutationFn: (expenseId: number) =>
      expenseService.deleteExpense(groupId, expenseId),
    onSuccess: () => {
      invalidations.expenseMutated(invalidate, groupId);
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
    mutationFn: () => groupService.removeMember(groupId, Number(user?.id)),
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
    }) => groupService.updateMemberRole(groupId, userId, role),
    onSuccess: (_, variables) => {
      invalidate(queryKeys.group(groupId));
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
      groupService.updateGroup(groupId, data),
    onSuccess: () => {
      invalidate(queryKeys.group(groupId));
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

  const isLoading = isGroupLoading || isFriendsLoading;

  return {
    id,
    groupId,
    navigate,
    user,
    group,
    isLoading,
    isBalancesLoading,
    isSettlementsLoading,
    isExpensesLoading,
    expenses,
    sortedExpenses,
    categories,
    memberNames,
    friends,
    balancesResponse,
    currentUserBalance,
    governance,
    activeTab,
    setActiveTab,
    isLeaveModalOpen,
    setIsLeaveModalOpen,
    isSelfDemoteModalOpen,
    setIsSelfDemoteModalOpen,
    isAddMemberModalOpen,
    setIsAddMemberModalOpen,
    isExpenseModalOpen,
    setIsExpenseModalOpen,
    editingExpense,
    setEditingExpense,
    isDeleteModalOpen,
    setIsDeleteModalOpen,
    expenseToDelete,
    setExpenseToDelete,
    handleAddExpense,
    handleEditExpense,
    handleDeleteExpenseClick,
    confirmDeleteExpense,
    handleLeave,
    handleRoleUpdate,
    confirmSelfDemote,
    leaveMutation,
    updateRoleMutation,
    updateGroupMutation,
    deleteExpenseMutation,
  };
}

export type GroupDetailsSession = ReturnType<typeof useGroupDetailsSession>;
