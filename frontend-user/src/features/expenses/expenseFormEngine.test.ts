import { describe, it, expect } from "vitest";
import { renderHook, act } from "@testing-library/react";
import {
  createInitialExpenseFormState,
  expenseFormReducer,
  useExpenseForm,
} from "./expenseFormEngine";
import type { Group } from "../../types/group";
import type { Expense } from "../../types/expense";

const mockGroup: Group = {
  id: 1,
  name: "Test Group",
  description: "Group for testing",
  members: [
    { id: 1, userId: 1, role: "ADMIN", joinedAt: "2025-01-01" },
    { id: 2, userId: 2, role: "MEMBER", joinedAt: "2025-01-01" },
    { id: 3, userId: 3, role: "MEMBER", joinedAt: "2025-01-01" },
  ],
  createdBy: 1,
  active: true,
  createdAt: "2025-01-01",
  updatedAt: "2025-01-01",
};

describe("expenseFormEngine — Reducer & Initializer", () => {
  it("initializes form state for new expense", () => {
    const initial = createInitialExpenseFormState(mockGroup, 1);
    expect(initial.description).toBe("");
    expect(initial.amount).toBe("");
    expect(initial.paidBy).toBe(1);
    expect(initial.splitType).toBe("EQUAL");
    expect(initial.selectedMembers).toEqual([1, 2, 3]);
    expect(initial.splitValues).toEqual({});
  });

  it("initializes form state from existing expense", () => {
    const existingExpense = {
      id: 10,
      groupId: 1,
      description: "Dinner",
      amount: 60,
      currency: "USD",
      paidBy: 2,
      categoryId: 5,
      expenseDate: "2026-08-01T12:00:00Z",
      splitType: "EXACT" as const,
      splits: [
        { id: 1, userId: 1, shareAmount: 20 },
        { id: 2, userId: 2, shareAmount: 40 },
      ],
      createdAt: "2026-08-01",
      updatedAt: "2026-08-01",
    };

    const initial = createInitialExpenseFormState(mockGroup, 1, existingExpense);
    expect(initial.description).toBe("Dinner");
    expect(initial.amount).toBe("60");
    expect(initial.paidBy).toBe(2);
    expect(initial.categoryId).toBe(5);
    expect(initial.expenseDate).toBe("2026-08-01");
    expect(initial.splitType).toBe("EXACT");
    expect(initial.selectedMembers).toEqual([1, 2]);
    expect(initial.splitValues).toEqual({ 1: "20", 2: "40" });
  });

  it("resets splitValues when changing splitType", () => {
    const state = createInitialExpenseFormState(mockGroup, 1);
    const withValues = expenseFormReducer(state, {
      type: "SET_SPLIT_VALUE",
      payload: { userId: 1, value: "50" },
    });
    expect(withValues.splitValues).toEqual({ 1: "50" });

    const changedType = expenseFormReducer(withValues, {
      type: "SET_SPLIT_TYPE",
      payload: "PERCENTAGE",
    });
    expect(changedType.splitType).toBe("PERCENTAGE");
    expect(changedType.splitValues).toEqual({});
  });

  it("toggles member selection and clears member split values", () => {
    const state = createInitialExpenseFormState(mockGroup, 1);
    const setType = expenseFormReducer(state, {
      type: "SET_SPLIT_TYPE",
      payload: "EXACT",
    });
    const withVal = expenseFormReducer(setType, {
      type: "SET_SPLIT_VALUE",
      payload: { userId: 2, value: "25" },
    });

    const toggledOff = expenseFormReducer(withVal, {
      type: "TOGGLE_MEMBER",
      payload: 2,
    });
    expect(toggledOff.selectedMembers).toEqual([1, 3]);
    expect(toggledOff.splitValues[2]).toBeUndefined();

    const toggledOn = expenseFormReducer(toggledOff, {
      type: "TOGGLE_MEMBER",
      payload: 2,
    });
    expect(toggledOn.selectedMembers).toEqual([1, 3, 2]);
  });
});

describe("useExpenseForm Hook", () => {
  it("computes validation, perShare, and payload for EQUAL split", () => {
    const { result } = renderHook(() =>
      useExpenseForm({ group: mockGroup, currentUserId: 1 }),
    );

    act(() => {
      result.current.setDescription("Groceries");
      result.current.setAmount("90");
    });

    expect(result.current.numAmount).toBe(90);
    expect(result.current.sharePerPerson).toBe("30.00");
    expect(result.current.validation.isValid).toBe(true);
    expect(result.current.isReadyToSubmit).toBe(true);

    const payload = result.current.getCreatePayload();
    expect(payload.description).toBe("Groceries");
    expect(payload.amount).toBe(90);
    expect(payload.splitType).toBe("EQUAL");
    expect(payload.splits).toHaveLength(3);
  });

  it("validates EXACT split totals", () => {
    const { result } = renderHook(() =>
      useExpenseForm({ group: mockGroup, currentUserId: 1 }),
    );

    act(() => {
      result.current.setDescription("Taxi");
      result.current.setAmount("100");
      result.current.setSplitType("EXACT");
      result.current.setSplitValue(1, "40");
      result.current.setSplitValue(2, "50");
    });

    // 40 + 50 = 90 !== 100
    expect(result.current.validation.isValid).toBe(false);
    expect(result.current.isReadyToSubmit).toBe(false);

    act(() => {
      result.current.setSplitValue(3, "10");
    });

    // 40 + 50 + 10 = 100 === 100
    expect(result.current.validation.isValid).toBe(true);
    expect(result.current.isReadyToSubmit).toBe(true);
  });
});
