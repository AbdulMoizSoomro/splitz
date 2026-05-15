export enum ActivityLogType {
    EXPENSE_CREATED = "EXPENSE_CREATED",
    EXPENSE_UPDATED = "EXPENSE_UPDATED",
    EXPENSE_DELETED = "EXPENSE_DELETED",
}

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
