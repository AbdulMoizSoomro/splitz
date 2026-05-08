import { describe, it, expect, vi, beforeEach } from 'vitest';
import { expenseService } from './expenseService';
import { expenseApi } from '../../lib/axios';

vi.mock('../../lib/axios', () => ({
  expenseApi: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
  default: {
    get: vi.fn(),
    post: vi.fn(),
  }
}));

describe('expenseService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches expenses for a group', async () => {
    const groupId = 1;
    const mockExpenses = [
      { id: 1, description: 'Lunch', amount: 30, splits: [] },
    ];
    vi.mocked(expenseApi.get).mockResolvedValueOnce({ data: mockExpenses });

    const result = await expenseService.getGroupExpenses(groupId);

    expect(expenseApi.get).toHaveBeenCalledWith(`/groups/${groupId}/expenses`);
    expect(result).toEqual(mockExpenses);
  });

  it('creates a new expense', async () => {
    const groupId = 1;
    const newExpense = {
      description: 'Dinner',
      amount: 60,
      paidBy: 1,
      splitType: 'EQUAL' as const,
      splits: [{ userId: 1 }, { userId: 2 }]
    };
    const mockResponse = { id: 2, ...newExpense, splits: [] };
    vi.mocked(expenseApi.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await expenseService.createExpense(groupId, newExpense);

    expect(expenseApi.post).toHaveBeenCalledWith(`/groups/${groupId}/expenses`, newExpense);
    expect(result).toEqual(mockResponse);
  });
});
