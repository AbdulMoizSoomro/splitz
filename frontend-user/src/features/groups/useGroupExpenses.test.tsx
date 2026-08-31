import { renderHook, waitFor, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useGroupExpenses } from "./useGroupExpenses";
import { expenseService } from "../expenses/expenseService";
import { categoryService } from "../expenses/categoryService";
import type { Expense } from "../../types/expense";
import type { ReactNode } from "react";

vi.mock("../expenses/expenseService");
vi.mock("../expenses/categoryService");

const mockExpenses: Expense[] = [
  {
    id: 1,
    description: "Lunch",
    amount: 20,
    paidBy: 1,
    groupId: 1,
    expenseDate: "2025-01-01T12:00:00Z",
    splits: [],
    splitType: "EQUAL",
  },
  {
    id: 2,
    description: "Dinner",
    amount: 40,
    paidBy: 1,
    groupId: 1,
    expenseDate: "2025-01-02T12:00:00Z",
    splits: [],
    splitType: "EQUAL",
  },
];

describe("useGroupExpenses", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
    });
    vi.clearAllMocks();
    vi.mocked(expenseService.getGroupExpenses).mockResolvedValue(mockExpenses);
    vi.mocked(categoryService.getCategories).mockResolvedValue([]);
  });

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it("fetches and sorts expenses descending by date", async () => {
    const { result } = renderHook(() => useGroupExpenses(1), { wrapper });

    await waitFor(() => {
      expect(result.current.expenses).toHaveLength(2);
      expect(result.current.sortedExpenses[0].id).toBe(2);
    });
  });

  it("manages expense edit and modal state", () => {
    const { result } = renderHook(() => useGroupExpenses(1), { wrapper });

    act(() => {
      result.current.handleAddExpense();
    });
    expect(result.current.isExpenseModalOpen).toBe(true);
    expect(result.current.editingExpense).toBeUndefined();

    act(() => {
      result.current.handleEditExpense(mockExpenses[0]);
    });
    expect(result.current.isExpenseModalOpen).toBe(true);
    expect(result.current.editingExpense).toEqual(mockExpenses[0]);
  });
});
