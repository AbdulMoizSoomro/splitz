export type SplitType = 'EQUAL' | 'EXACT';

export interface ExpenseSplit {
  id: number;
  userId: number;
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
