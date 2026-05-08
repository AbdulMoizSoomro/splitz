import { expenseApi } from '../../lib/axios';
import type { Expense, CreateExpenseRequest } from '../../types/expense';

export const expenseService = {
  getGroupExpenses: async (groupId: number): Promise<Expense[]> => {
    const response = await expenseApi.get<Expense[]>(`/groups/${groupId}/expenses`);
    return response.data;
  },

  createExpense: async (groupId: number, data: CreateExpenseRequest): Promise<Expense> => {
    const response = await expenseApi.post<Expense>(`/groups/${groupId}/expenses`, data);
    return response.data;
  },

  deleteExpense: async (groupId: number, expenseId: number): Promise<void> => {
    await expenseApi.delete(`/groups/${groupId}/expenses/${expenseId}`);
  },
};
