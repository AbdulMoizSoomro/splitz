export interface User {
  id: number;
  username: string;
  email: string;
  firstName: string;
  lastName?: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type FriendshipStatus = "PENDING" | "ACCEPTED" | "REJECTED" | "BLOCKED";

export interface Friendship {
  id: number;
  requesterId: number;
  addresseeId: number;
  status: FriendshipStatus;
  createdAt: string;
  updatedAt: string;
}

export interface GroupBalance {
  groupId: number;
  groupName: string;
  balance: number;
}

export interface UserBalanceResponse {
  userId: number;
  username: string;
  email: string;
  totalBalance: number;
  groupBalances: GroupBalance[];
}

export interface FriendshipSettlementDTO {
  id: number;
  payerId: number;
  payeeId: number;
  amount: number;
  status: "PENDING" | "MARKED_PAID" | "COMPLETED";
  createdAt: string;
  updatedAt: string;
  markedPaidAt?: string;
  settledAt?: string;
}
