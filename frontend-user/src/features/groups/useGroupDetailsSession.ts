import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { useGroupExpenses } from "./useGroupExpenses";
import { useGroupMembership } from "./useGroupMembership";
import { useGroupBalancesData } from "./useGroupBalancesData";

export type TabType = "expenses" | "members" | "balances";

export function useGroupDetailsSession() {
  const { id } = useParams<{ id: string }>();
  const groupId = Number(id);
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const [activeTab, setActiveTab] = useState<TabType>("expenses");

  const membership = useGroupMembership(groupId, Number(user?.id));
  const expenses = useGroupExpenses(groupId);
  const balances = useGroupBalancesData(
    groupId,
    Number(user?.id),
    membership.group,
  );

  const isLoading = membership.isGroupLoading || membership.isFriendsLoading;

  return {
    id,
    groupId,
    navigate,
    user,
    group: membership.group,
    isLoading,
    isBalancesLoading: balances.isBalancesLoading,
    isSettlementsLoading: balances.isSettlementsLoading,
    isExpensesLoading: expenses.isExpensesLoading,
    expenses: expenses.expenses,
    sortedExpenses: expenses.sortedExpenses,
    categories: expenses.categories,
    memberNames: membership.memberNames,
    friends: membership.friends,
    balancesResponse: balances.balancesResponse,
    currentUserBalance: balances.currentUserBalance,
    governance: balances.governance,
    activeTab,
    setActiveTab,
    isLeaveModalOpen: membership.isLeaveModalOpen,
    setIsLeaveModalOpen: membership.setIsLeaveModalOpen,
    isSelfDemoteModalOpen: membership.isSelfDemoteModalOpen,
    setIsSelfDemoteModalOpen: membership.setIsSelfDemoteModalOpen,
    isAddMemberModalOpen: membership.isAddMemberModalOpen,
    setIsAddMemberModalOpen: membership.setIsAddMemberModalOpen,
    isExpenseModalOpen: expenses.isExpenseModalOpen,
    setIsExpenseModalOpen: expenses.setIsExpenseModalOpen,
    editingExpense: expenses.editingExpense,
    setEditingExpense: expenses.setEditingExpense,
    isDeleteModalOpen: expenses.isDeleteModalOpen,
    setIsDeleteModalOpen: expenses.setIsDeleteModalOpen,
    expenseToDelete: expenses.expenseToDelete,
    setExpenseToDelete: expenses.setExpenseToDelete,
    handleAddExpense: expenses.handleAddExpense,
    handleEditExpense: expenses.handleEditExpense,
    handleDeleteExpenseClick: expenses.handleDeleteExpenseClick,
    confirmDeleteExpense: expenses.confirmDeleteExpense,
    handleLeave: membership.handleLeave,
    handleRoleUpdate: membership.handleRoleUpdate,
    confirmSelfDemote: membership.confirmSelfDemote,
    leaveMutation: membership.leaveMutation,
    updateRoleMutation: membership.updateRoleMutation,
    updateGroupMutation: membership.updateGroupMutation,
    deleteExpenseMutation: expenses.deleteExpenseMutation,
  };
}

export type GroupDetailsSession = ReturnType<typeof useGroupDetailsSession>;
