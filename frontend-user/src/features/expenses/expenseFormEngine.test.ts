import { describe, it, expect } from "vitest";
import { renderHook, act } from "@testing-library/react";
import {
  createInitialExpenseFormState,
  expenseFormReducer,
  useExpenseForm,
  placeholder,
  unitPrefix,
  unitSuffix,
  splitTypeLabel,
  validateSplit,
  perShare,
  parseAmount,
  buildSplits,
  type SplitFormValues,
} from "./expenseFormEngine";
import type { Group } from "../../types/group";

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
  allowMembersToManageMembers: false,
  allowMembersToEditExpenses: false,
  createdAt: "2025-01-01",
  updatedAt: "2025-01-01",
};

/** Builds a valid split form, overriding fields per test. */
function buildTestForm(over: Partial<SplitFormValues> = {}): SplitFormValues {
  return {
    splitType: "EXACT",
    amount: 30,
    splitValues: { 1: "10", 2: "10", 3: "10" },
    selectedMembers: [1, 2, 3],
    ...over,
  };
}

describe("expenseFormEngine — UI Formatters", () => {
  describe("placeholder", () => {
    it("returns 0.00 for EXACT", () => {
      expect(placeholder("EXACT")).toBe("0.00");
    });
    it("returns 0 for PERCENTAGE", () => {
      expect(placeholder("PERCENTAGE")).toBe("0");
    });
    it("returns 1 for SHARES", () => {
      expect(placeholder("SHARES")).toBe("1");
    });
    it("returns 0.00 for ADJUSTMENT", () => {
      expect(placeholder("ADJUSTMENT")).toBe("0.00");
    });
    it("returns empty string for EQUAL", () => {
      expect(placeholder("EQUAL")).toBe("");
    });
  });

  describe("unitPrefix / unitSuffix", () => {
    it("returns $ for EXACT and ADJUSTMENT", () => {
      expect(unitPrefix("EXACT")).toBe("$");
      expect(unitPrefix("ADJUSTMENT")).toBe("$");
    });
    it("returns empty string for PERCENTAGE and SHARES", () => {
      expect(unitPrefix("PERCENTAGE")).toBe("");
      expect(unitPrefix("SHARES")).toBe("");
    });
    it("returns % suffix for PERCENTAGE", () => {
      expect(unitSuffix("PERCENTAGE")).toBe("%");
    });
    it("returns ' shares' suffix for SHARES", () => {
      expect(unitSuffix("SHARES")).toBe(" shares");
    });
    it("returns empty suffix for EXACT and ADJUSTMENT", () => {
      expect(unitSuffix("EXACT")).toBe("");
      expect(unitSuffix("ADJUSTMENT")).toBe("");
    });
  });

  describe("splitTypeLabel", () => {
    it("returns 'Fixed Adjustment' for ADJUSTMENT", () => {
      expect(splitTypeLabel("ADJUSTMENT")).toBe("Fixed Adjustment");
    });
    it("returns the lowercased type for others", () => {
      expect(splitTypeLabel("EXACT")).toBe("exact");
      expect(splitTypeLabel("PERCENTAGE")).toBe("percentage");
    });
  });
});

describe("expenseFormEngine — Split Calculations & Validation", () => {
  describe("validateSplit", () => {
    it("marks an EXACT split valid when values sum to the amount", () => {
      const result = validateSplit(buildTestForm());
      expect(result.isValid).toBe(true);
      expect(result.message).toBe("Fully allocated");
      expect(result.error).toBeUndefined();
    });

    it("marks an EXACT split invalid with a remaining message when under-allocated", () => {
      const result = validateSplit(
        buildTestForm({ splitValues: { 1: "10", 2: "10", 3: "0" } }),
      );
      expect(result.isValid).toBe(false);
      expect(result.message).toBe("Remaining: $10.00");
      expect(result.error).toBe("Total must equal $30");
    });

    it("marks a PERCENTAGE split valid when values sum to 100", () => {
      const result = validateSplit(
        buildTestForm({ splitType: "PERCENTAGE", splitValues: { 1: "50", 2: "50" } }),
      );
      expect(result.isValid).toBe(true);
      expect(result.message).toBe("100% allocated");
    });

    it("marks a PERCENTAGE split invalid when values do not sum to 100", () => {
      const result = validateSplit(
        buildTestForm({ splitType: "PERCENTAGE", splitValues: { 1: "40", 2: "50" } }),
      );
      expect(result.isValid).toBe(false);
      expect(result.error).toBe("Total must equal 100%");
    });

    it("marks a SHARES split valid when total shares are positive", () => {
      const result = validateSplit(
        buildTestForm({ splitType: "SHARES", splitValues: { 1: "1", 2: "3" } }),
      );
      expect(result.isValid).toBe(true);
      expect(result.message).toBe("Total shares: 4");
    });

    it("marks a SHARES split invalid when total shares are zero", () => {
      const result = validateSplit(
        buildTestForm({ splitType: "SHARES", splitValues: { 1: "0", 2: "0" } }),
      );
      expect(result.isValid).toBe(false);
      expect(result.error).toBe("Total shares must be greater than 0");
    });

    it("marks an ADJUSTMENT split valid when values sum to zero", () => {
      const result = validateSplit(
        buildTestForm({ splitType: "ADJUSTMENT", splitValues: { 1: "5", 2: "-5" } }),
      );
      expect(result.isValid).toBe(true);
      expect(result.message).toBe("Adjustments balanced");
    });

    it("marks an ADJUSTMENT split invalid with an offset when non-zero", () => {
      const result = validateSplit(
        buildTestForm({ splitType: "ADJUSTMENT", splitValues: { 1: "5", 2: "0" } }),
      );
      expect(result.isValid).toBe(false);
      expect(result.error).toBe("Adjustments must sum to $0.00");
    });

    it("always marks an EQUAL split valid", () => {
      const result = validateSplit(buildTestForm({ splitType: "EQUAL" }));
      expect(result.isValid).toBe(true);
    });
  });

  describe("perShare", () => {
    it("divides the amount evenly among members", () => {
      expect(perShare(buildTestForm())).toBe("10.00");
    });

    it("returns 0.00 when there are no members", () => {
      expect(perShare(buildTestForm({ selectedMembers: [] }))).toBe("0.00");
    });

    it("returns 0.00 when the amount is zero", () => {
      expect(perShare(buildTestForm({ amount: 0 }))).toBe("0.00");
    });
  });

  describe("parseAmount", () => {
    it("parses a numeric string", () => {
      expect(parseAmount("42.50")).toBe(42.5);
    });

    it("returns 0 for an empty string", () => {
      expect(parseAmount("")).toBe(0);
    });

    it("returns 0 for non-numeric input", () => {
      expect(parseAmount("abc")).toBe(0);
    });
  });

  describe("buildSplits", () => {
    it("builds EXACT splits with splitValue and shareAmount per member", () => {
      const result = buildSplits(buildTestForm());
      expect(result).toEqual([
        { userId: 1, splitType: "EXACT", splitValue: 10, shareAmount: 10 },
        { userId: 2, splitType: "EXACT", splitValue: 10, shareAmount: 10 },
        { userId: 3, splitType: "EXACT", splitValue: 10, shareAmount: 10 },
      ]);
    });

    it("builds EQUAL splits with undefined splitValue and shareAmount", () => {
      const result = buildSplits(buildTestForm({ splitType: "EQUAL" }));
      expect(result).toEqual([
        { userId: 1, splitType: "EQUAL", splitValue: undefined, shareAmount: undefined },
        { userId: 2, splitType: "EQUAL", splitValue: undefined, shareAmount: undefined },
        { userId: 3, splitType: "EQUAL", splitValue: undefined, shareAmount: undefined },
      ]);
    });

    it("treats missing split values as zero for non-EQUAL splits", () => {
      const result = buildSplits(
        buildTestForm({ splitType: "PERCENTAGE", selectedMembers: [1], splitValues: {} }),
      );
      expect(result).toEqual([
        { userId: 1, splitType: "PERCENTAGE", splitValue: 0, shareAmount: undefined },
      ]);
    });
  });
});

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
    // Mirrors what GET /groups/{id}/expenses actually returns. Note the absence of `splitType`:
    // ExpenseDTO omits it (it exists only on the Create/Update requests), so the form cannot know
    // how the original expense was split and falls back to EQUAL. Split *amounts* still round-trip,
    // because those come from `splits[].shareAmount`.
    const existingExpense = {
      id: 10,
      groupId: 1,
      description: "Dinner",
      amount: 60,
      currency: "USD",
      paidBy: 2,
      categoryId: 5,
      expenseDate: "2026-08-01T12:00:00Z",
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
    // Falls back to EQUAL because the API does not report the original split type.
    expect(initial.splitType).toBe("EQUAL");
    expect(initial.selectedMembers).toEqual([1, 2]);
    // Per-member amounts are only seeded for non-EQUAL split types, so this stays empty.
    expect(initial.splitValues).toEqual({});
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
