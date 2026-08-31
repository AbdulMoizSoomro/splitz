import api, { expenseApi } from "../../lib/axios";
import type {
  User,
  Friendship,
  FriendshipSettlementDTO,
  FriendBalanceResponse,
} from "../../types/user";

import { settlementService } from "../balances/settlementService";

export const friendService = {
  getFriends: async (userId: string | number): Promise<User[]> => {
    const response = await api.get<User[]>(`/users/${userId}/friends`);
    return response.data;
  },

  getFriendRequests: async (
    userId: string | number,
    direction: "INCOMING" | "OUTGOING",
  ): Promise<Friendship[]> => {
    const response = await api.get<Friendship[]>(
      `/users/${userId}/friends/requests?direction=${direction}`,
    );
    return response.data;
  },

  sendFriendRequest: async (
    userId: string | number,
    friendId: number,
  ): Promise<Friendship> => {
    const response = await api.post<Friendship>(
      `/users/${userId}/friends?friendId=${friendId}`,
    );
    return response.data;
  },

  respondToFriendRequest: async (
    userId: string | number,
    friendshipId: number,
    action: "accept" | "reject",
  ): Promise<Friendship> => {
    const response = await api.put<Friendship>(
      `/users/${userId}/friends/${friendshipId}/${action}`,
    );
    return response.data;
  },

  removeFriend: async (
    userId: string | number,
    friendId: number,
  ): Promise<void> => {
    await api.delete(`/users/${userId}/friends/${friendId}`);
  },

  getNetBalance: async (
    userId: number,
    friendId: number,
  ): Promise<FriendBalanceResponse> => {
    const response = await expenseApi.get<FriendBalanceResponse>(
      `/users/${userId}/balances/with/${friendId}`,
    );
    return response.data;
  },

  getSettlementsWithFriend: async (
    userId: number,
    friendId: number,
  ): Promise<FriendshipSettlementDTO[]> => {
    return settlementService.getSettlementsBetweenUsers(userId, friendId);
  },

  createSettlement: async (data: {
    payerId: number;
    payeeId: number;
    amount: number;
    allocations?: { groupId: number; amount: number }[];
  }): Promise<FriendshipSettlementDTO> => {
    return settlementService.createSettlement(data);
  },

  markAsPaid: async (
    settlementId: number,
  ): Promise<FriendshipSettlementDTO> => {
    return settlementService.markAsPaid(settlementId);
  },

  confirmSettlement: async (
    settlementId: number,
  ): Promise<FriendshipSettlementDTO> => {
    return settlementService.confirmSettlement(settlementId);
  },

  updateSettlement: async (
    settlementId: number,
    data: {
      amount: number;
      allocations?: { groupId: number; amount: number }[];
    },
  ): Promise<FriendshipSettlementDTO> => {
    return settlementService.updateSettlement(settlementId, data);
  },
};

