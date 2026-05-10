import api, { expenseApi } from "../../lib/axios";
import type {
  User,
  Friendship,
  FriendshipSettlementDTO,
} from "../../types/user";

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
  ): Promise<{ netBalance: number }> => {
    const response = await expenseApi.get<{ netBalance: number }>(
      `/users/${userId}/balances/with/${friendId}`,
    );
    return response.data;
  },

  getSettlementsWithFriend: async (
    userId: number,
    friendId: number,
  ): Promise<FriendshipSettlementDTO[]> => {
    const response = await expenseApi.get<FriendshipSettlementDTO[]>(
      `/users/${userId}/friendships/${friendId}/settlements`,
    );
    return response.data;
  },

  createSettlement: async (data: {
    payerId: number;
    payeeId: number;
    amount: number;
  }): Promise<FriendshipSettlementDTO> => {
    const response = await expenseApi.post<FriendshipSettlementDTO>(
      "/friendship-settlements",
      data,
    );
    return response.data;
  },

  markAsPaid: async (
    settlementId: number,
  ): Promise<FriendshipSettlementDTO> => {
    const response = await expenseApi.put<FriendshipSettlementDTO>(
      `/friendship-settlements/${settlementId}/mark-paid`,
    );
    return response.data;
  },

  confirmSettlement: async (
    settlementId: number,
  ): Promise<FriendshipSettlementDTO> => {
    const response = await expenseApi.put<FriendshipSettlementDTO>(
      `/friendship-settlements/${settlementId}/confirm`,
    );
    return response.data;
  },
};
