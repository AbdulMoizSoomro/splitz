export type GroupRole = 'ADMIN' | 'MEMBER';

export interface GroupMember {
  id: number;
  userId: number;
  role: GroupRole;
  joinedAt: string;
}

export interface Group {
  id: number;
  name: string;
  description?: string;
  createdBy: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  members: GroupMember[];
}

export interface CreateGroupRequest {
  name: string;
  description?: string;
  memberUserIds?: number[];
}

export interface AddMemberRequest {
  userId: number;
  role?: GroupRole;
}

export interface Balance {
  userId: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  balance: number;
}

export interface Debt {
  from: number;
  to: number;
  amount: number;
}

export interface GroupBalanceResponse {
  groupId: number;
  balances: Balance[];
  simplifiedDebts: Debt[];
}
