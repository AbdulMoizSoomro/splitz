import { expenseApi } from "../../lib/axios";

export interface CreateSettlementRequest {
  payerId: number;
  payeeId: number;
  amount: number;
  currency: string;
  groupId: number;
}

export interface Settlement {
  id: number;
  payerId: number;
  payeeId: number;
  amount: number;
  currency: string;
  groupId: number;
  status: "PENDING" | "PAID" | "CONFIRMED" | "REJECTED";
  createdAt: string;
  paidAt?: string;
  confirmedAt?: string;
  updatedAt: string;
  markedPaidAt?: string;
  settledAt?: string;
}

export const settlementService = {
  createSettlement: async (
    data: CreateSettlementRequest,
  ): Promise<Settlement> => {
    const response = await expenseApi.post<Settlement>("/settlements", data);
    return response.data;
  },

  markAsPaid: async (id: number): Promise<Settlement> => {
    const response = await expenseApi.put<Settlement>(
      `/settlements/${id}/mark-paid`,
    );
    return response.data;
  },

  confirmSettlement: async (id: number): Promise<Settlement> => {
    const response = await expenseApi.put<Settlement>(
      `/settlements/${id}/confirm`,
    );
    return response.data;
  },

  getSettlementsByGroup: async (groupId: number): Promise<Settlement[]> => {
    const response = await expenseApi.get<Settlement[]>(
      `/groups/${groupId}/settlements`,
    );
    return response.data;
  },
};
