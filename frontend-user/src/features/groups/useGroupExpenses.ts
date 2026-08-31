import { useState, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { expenseService } from "../expenses/expenseService";
import { categoryService } from "../expenses/categoryService";
import { queryKeys, invalidations, bindInvalidations } from "../../lib/queryKeys";
import { toast } from "sonner";
import type { Expense } from "../../types/expense";

export function useGroupExpenses(groupId: number) {
  const queryClient = useQueryClient();
  const invalidate = bindInvalidations(queryClient);

  const [isExpenseModalOpen, setIsExpenseModalOpen] = useState(false);
  const [editingExpense, setEditingExpense] = useState<Expense | undefined>(
    undefined,
  );
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [expenseToDelete, setExpenseToDelete] = useState<Expense | null>(null);

  const { data: expenses, isLoading: isExpensesLoading } = useQuery({
    queryKey: queryKeys.expenses(groupId),
    queryFn: () => expenseService.getGroupExpenses(groupId),
    enabled: !!groupId,
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

  const handleAddExpense = () => {
    setEditingExpense(undefined);
    setIsExpenseModalOpen(true);
  };

  const handleEditExpense = (expense: Expense) => {
    setEditingExpense(expense);
    setIsExpenseModalOpen(true);
  };

  const handleDeleteExpenseClick = (expense: Expense) => {
    setExpenseToDelete(expense);
    setIsDeleteModalOpen(true);
  };

  const confirmDeleteExpense = () => {
    if (expenseToDelete) {
      deleteExpenseMutation.mutate(expenseToDelete.id);
    }
  };

  return {
    expenses,
    sortedExpenses,
    isExpensesLoading,
    categories,
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
    deleteExpenseMutation,
  };
}
