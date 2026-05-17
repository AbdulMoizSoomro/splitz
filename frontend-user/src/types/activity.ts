export const ActivityLogType = {
  EXPENSE_CREATED: "EXPENSE_CREATED",
  EXPENSE_UPDATED: "EXPENSE_UPDATED",
  EXPENSE_DELETED: "EXPENSE_DELETED",
} as const;

export type ActivityLogType =
  (typeof ActivityLogType)[keyof typeof ActivityLogType];

export interface ActivityLog {
  id: number;
  groupId: number;
  type: ActivityLogType;
  actorId: number;
  entityId: number;
  entityName: string;
  timestamp: string;
  details?: string;
}
