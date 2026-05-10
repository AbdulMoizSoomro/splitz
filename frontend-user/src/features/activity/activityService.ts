import { expenseApi } from "../../lib/axios";
import type { Expense } from "../../types/expense";
import type { Settlement } from "../balances/settlementService";

export interface GlobalActivity {
  expenses: Expense[];
  settlements: Settlement[];
}

export const activityService = {
  getGlobalActivity: async (): Promise<GlobalActivity> => {
    const response = await expenseApi.get<GlobalActivity>("/activity");
    return response.data;
  },
};
