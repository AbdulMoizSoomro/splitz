import { expenseApi } from "../../lib/axios";
import type {
  Expense,
  CreateExpenseRequest,
  UpdateExpenseRequest,
} from "../../types/expense";

export const expenseService = {
  getGroupExpenses: async (groupId: number): Promise<Expense[]> => {
    const response = await expenseApi.get<Expense[]>(
      `/groups/${groupId}/expenses`,
    );
    return response.data;
  },

  getBulkGroupExpenses: async (groupIds: number[]): Promise<Expense[]> => {
    if (groupIds.length === 0) return [];
    const response = await expenseApi.get<Expense[]>(
      `/groups/expenses/bulk?groupIds=${groupIds.join(",")}`,
    );
    return response.data;
  },

  createExpense: async (
    groupId: number,
    data: CreateExpenseRequest,
  ): Promise<Expense> => {
    const response = await expenseApi.post<Expense>(
      `/groups/${groupId}/expenses`,
      data,
    );
    return response.data;
  },

  updateExpense: async (
    groupId: number,
    expenseId: number,
    data: UpdateExpenseRequest,
  ): Promise<Expense> => {
    const response = await expenseApi.put<Expense>(
      `/groups/${groupId}/expenses/${expenseId}`,
      data,
    );
    return response.data;
  },

  deleteExpense: async (groupId: number, expenseId: number): Promise<void> => {
    await expenseApi.delete(`/groups/${groupId}/expenses/${expenseId}`);
  },
};
