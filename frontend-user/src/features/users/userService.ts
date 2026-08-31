import api from "../../lib/axios";
import type { User } from "../../types/user";

export const userService = {
  getUser: async (userId: number | string): Promise<User | null> => {
    try {
      const response = await api.get<User>(`/users/${userId}`);
      return response.data;
    } catch {
      return null;
    }
  },

  getCurrentUser: async (): Promise<User> => {
    const response = await api.get<User>("/users/me");
    return response.data;
  },
};
