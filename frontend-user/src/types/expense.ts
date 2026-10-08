export type SplitType =
  | "EQUAL"
  | "EXACT"
  | "PERCENTAGE"
  | "SHARES"
  | "ADJUSTMENT";

export interface ExpenseSplit {
  id: number;
  userId: number;
  /** How the expense was split. Lets an edit form restore the original split inputs. */
  splitType: SplitType;
  /**
   * The per-member input the split was computed from: an amount for `EXACT`, a percentage for
   * `PERCENTAGE`, a share count for `SHARES`, a delta for `ADJUSTMENT`. Null for `EQUAL`, which has
   * no per-member input.
   */
  splitValue?: number | null;
  shareAmount: number;
}

export interface Expense {
  id: number;
  groupId: number;
  description: string;
  amount: number;
  currency: string;
  paidBy: number;
  categoryId?: number;
  expenseDate: string;
  notes?: string;
  receiptUrl?: string;
  lastModifiedBy?: number;
  splits: ExpenseSplit[];
  createdAt: string;
  updatedAt: string;
}

export interface SplitRequest {
  userId: number;
  splitType?: SplitType;
  splitValue?: number;
  shareAmount?: number;
}

export interface CreateExpenseRequest {
  description: string;
  amount: number;
  currency?: string;
  paidBy: number;
  categoryId?: number;
  expenseDate?: string;
  notes?: string;
  receiptUrl?: string;
  splitType: SplitType;
  splits: SplitRequest[];
}

export interface UpdateExpenseRequest {
  description?: string;
  amount?: number;
  currency?: string;
  paidBy?: number;
  categoryId?: number;
  expenseDate?: string;
  notes?: string;
  receiptUrl?: string;
  splitType?: SplitType;
  splits?: SplitRequest[];
}
