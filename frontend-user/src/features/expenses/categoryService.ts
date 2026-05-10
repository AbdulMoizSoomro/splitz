import { expenseApi } from "../../lib/axios";

export interface Category {
  id: number;
  name: string;
  icon?: string;
  color?: string;
  defaultCategory: boolean;
}

export const categoryService = {
  getCategories: async (): Promise<Category[]> => {
    const response = await expenseApi.get<Category[]>("/categories");
    return response.data;
  },
};
