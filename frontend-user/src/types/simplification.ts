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

/**
 * Account-level debt simplification preference. An opt-out here is a hard override: the user is
 * excluded from netting in every group, including groups configured before the opt-out.
 */
export interface UserSimplificationPreference {
  userId: number;
  accountOptOut: boolean;
}
