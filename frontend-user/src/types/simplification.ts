export interface SimplifiedDebtTransaction {
  fromUserId: number;
  fromUsername: string;
  toUserId: number;
  toUsername: string;
  amount: number;
  status: string;
}

export interface DebtSimplificationPlan {
  groupId: number;
  scope: 'INTRA_GROUP' | 'CROSS_GROUP';
  status: string;
  simplificationEnabled: boolean;
  originalTransactionCount: number;
  simplifiedTransactionCount: number;
  totalDebtVolume: number;
  optedOutUserIds: number[];
  transactions: SimplifiedDebtTransaction[];
}

export interface GroupSimplificationSettings {
  groupId: number;
  simplificationEnabled: boolean;
  simplificationScope: 'INTRA_GROUP' | 'CROSS_GROUP';
  optOutUserIds: number[];
}

export interface UpdateSimplificationSettingsRequest {
  simplificationEnabled: boolean;
  simplificationScope: 'INTRA_GROUP' | 'CROSS_GROUP';
}

export interface UserOptOutRequest {
  optOut: boolean;
}
