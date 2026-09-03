import { expenseApi } from "../../lib/axios";

export interface CreateSettlementRequest {
  payerId: number;
  payeeId: number;
  amount: number;
  currency?: string;
  groupId?: number | null;
  type?: "GROUP" | "DIRECT";
  notes?: string | null;
  allocations?: Array<{
    groupId: number | null;
    amount: number;
  }>;
}

export interface UpdateSettlementRequest {
  amount: number;
  notes?: string | null;
  allocations?: Array<{
    groupId: number;
    amount: number;
  }>;
}

export interface Settlement {
  id: number;
  payerId: number;
  payeeId: number;
  amount: number;
  currency?: string;
  type?: "GROUP" | "DIRECT";
  groupId?: number | null;
  notes?: string | null;
  status: "PENDING" | "MARKED_PAID" | "COMPLETED";
  createdAt: string;
  paidAt?: string;
  confirmedAt?: string;
  updatedAt: string;
  markedPaidAt?: string;
  settledAt?: string;
  allocations?: Array<{
    groupId: number | null;
    amount: number;
  }>;
}

export const settlementService = {
  createSettlement: async (
    data: CreateSettlementRequest,
  ): Promise<Settlement> => {
    const response = await expenseApi.post<Settlement>("/settlements", data);
    return response.data;
  },

  createGroupPayment: async (
    groupId: number,
    data: Omit<CreateSettlementRequest, "groupId">,
  ): Promise<Settlement> => {
    const response = await expenseApi.post<Settlement>(
      `/groups/${groupId}/payments`,
      data,
    );
    return response.data;
  },

  createDirectPayment: async (
    data: Omit<CreateSettlementRequest, "groupId">,
  ): Promise<Settlement> => {
    const response = await expenseApi.post<Settlement>(
      "/payments/direct",
      data,
    );
    return response.data;
  },

  getSettlement: async (id: number): Promise<Settlement> => {
    const response = await expenseApi.get<Settlement>(`/settlements/${id}`);
    return response.data;
  },

  getSettlementsByGroup: async (groupId: number): Promise<Settlement[]> => {
    const response = await expenseApi.get<Settlement[]>(
      `/groups/${groupId}/settlements`,
    );
    return response.data;
  },

  getSettlementsBetweenUsers: async (
    userId1: number,
    userId2: number,
  ): Promise<Settlement[]> => {
    const response = await expenseApi.get<Settlement[]>(
      `/users/${userId1}/friendships/${userId2}/settlements`,
    );
    return response.data;
  },

  updateSettlement: async (
    id: number,
    data: UpdateSettlementRequest,
  ): Promise<Settlement> => {
    const response = await expenseApi.put<Settlement>(`/settlements/${id}`, data);
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
};
