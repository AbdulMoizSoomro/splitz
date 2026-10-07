import { expenseApi } from "../../lib/axios";
import type {
  DebtSimplificationPlan,
  GroupSimplificationSettings,
  UpdateSimplificationSettingsRequest,
  UserOptOutRequest,
  UserSimplificationPreference,
} from "../../types/simplification";

export const simplificationService = {
  getSimplificationPlan: async (
    groupId: number,
  ): Promise<DebtSimplificationPlan> => {
    const response = await expenseApi.get<DebtSimplificationPlan>(
      `/groups/${groupId}/simplification-plan`,
    );
    return response.data;
  },

  getSimplificationSettings: async (
    groupId: number,
  ): Promise<GroupSimplificationSettings> => {
    const response = await expenseApi.get<GroupSimplificationSettings>(
      `/groups/${groupId}/simplification-settings`,
    );
    return response.data;
  },

  updateSimplificationSettings: async (
    groupId: number,
    data: UpdateSimplificationSettingsRequest,
  ): Promise<GroupSimplificationSettings> => {
    const response = await expenseApi.put<GroupSimplificationSettings>(
      `/groups/${groupId}/simplification-settings`,
      data,
    );
    return response.data;
  },

  toggleUserOptOut: async (
    groupId: number,
    data: UserOptOutRequest,
  ): Promise<GroupSimplificationSettings> => {
    const response = await expenseApi.post<GroupSimplificationSettings>(
      `/groups/${groupId}/simplification-settings/opt-out`,
      data,
    );
    return response.data;
  },

  getAccountPreference: async (): Promise<UserSimplificationPreference> => {
    const response = await expenseApi.get<UserSimplificationPreference>(
      "/simplification-preferences/me",
    );
    return response.data;
  },

  toggleAccountOptOut: async (
    data: UserOptOutRequest,
  ): Promise<UserSimplificationPreference> => {
    const response = await expenseApi.post<UserSimplificationPreference>(
      "/simplification-preferences/me/opt-out",
      data,
    );
    return response.data;
  },
};
