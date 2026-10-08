import { useReducer, useMemo, useCallback } from "react";
import type { Group } from "../../types/group";
import type {
  Expense,
  SplitType,
  SplitRequest,
  CreateExpenseRequest,
  UpdateExpenseRequest,
} from "../../types/expense";

// --- Form & Calculation Interfaces ---

export interface SplitFormValues {
  splitType: SplitType;
  /** Parsed numeric amount. */
  amount: number;
  /** Per-member split values, keyed by userId, still in string form. */
  splitValues: Record<number, string>;
  selectedMembers: number[];
}

export interface ValidationResult {
  isValid: boolean;
  message?: string;
  error?: string;
}

interface SplitFormat {
  placeholder: string;
  prefix: string;
  suffix: string;
  label: string;
}

// --- UI Formatters & Presentation Specs ---

const FORMAT: Record<SplitType, SplitFormat> = {
  EQUAL: { placeholder: "", prefix: "", suffix: "", label: "equal" },
  EXACT: { placeholder: "0.00", prefix: "$", suffix: "", label: "exact" },
  PERCENTAGE: { placeholder: "0", prefix: "", suffix: "%", label: "percentage" },
  SHARES: { placeholder: "1", prefix: "", suffix: " shares", label: "shares" },
  ADJUSTMENT: {
    placeholder: "0.00",
    prefix: "$",
    suffix: "",
    label: "Fixed Adjustment",
  },
};

export function placeholder(splitType: SplitType): string {
  return FORMAT[splitType].placeholder;
}

export function unitPrefix(splitType: SplitType): string {
  return FORMAT[splitType].prefix;
}

export function unitSuffix(splitType: SplitType): string {
  return FORMAT[splitType].suffix;
}

export function splitTypeLabel(splitType: SplitType): string {
  return FORMAT[splitType].label;
}

// --- Math & Split Calculations ---

/** Coerces a raw input string to a number, treating empty/non-numeric as 0. */
export function parseAmount(amount: string): number {
  return parseFloat(amount) || 0;
}

function totalSplitValue(splitValues: Record<number, string>): number {
  return Object.values(splitValues).reduce(
    (sum, val) => sum + parseAmount(val),
    0,
  );
}

export function validateSplit(form: SplitFormValues): ValidationResult {
  const { splitType, amount } = form;
  const total = totalSplitValue(form.splitValues);

  switch (splitType) {
    case "EQUAL":
      return { isValid: true };
    case "EXACT": {
      const isExactValid = Math.abs(total - amount) < 0.01;
      return {
        isValid: isExactValid,
        message: isExactValid
          ? "Fully allocated"
          : `Remaining: $${(amount - total).toFixed(2)}`,
        error:
          !isExactValid && amount > 0
            ? `Total must equal $${amount}`
            : undefined,
      };
    }
    case "PERCENTAGE": {
      const isPercentValid = Math.abs(total - 100) < 0.01;
      return {
        isValid: isPercentValid,
        message: isPercentValid
          ? "100% allocated"
          : `Total: ${total.toFixed(1)}%`,
        error: !isPercentValid ? "Total must equal 100%" : undefined,
      };
    }
    case "SHARES": {
      const hasShares = total > 0;
      return {
        isValid: hasShares,
        message: `Total shares: ${total}`,
        error: !hasShares ? "Total shares must be greater than 0" : undefined,
      };
    }
    case "ADJUSTMENT": {
      const isAdjValid = Math.abs(total) < 0.01;
      return {
        isValid: isAdjValid,
        message: isAdjValid
          ? "Adjustments balanced"
          : `Offset: ${total > 0 ? "+" : ""}$${total.toFixed(2)}`,
        error: !isAdjValid ? "Adjustments must sum to $0.00" : undefined,
      };
    }
    default:
      return { isValid: true };
  }
}

export function perShare(form: SplitFormValues): string {
  const { amount, selectedMembers } = form;
  return amount && selectedMembers.length > 0
    ? (amount / selectedMembers.length).toFixed(2)
    : "0.00";
}

export function buildSplits(form: SplitFormValues): SplitRequest[] {
  const { selectedMembers, splitType, splitValues } = form;
  return selectedMembers.map((userId) => ({
    userId,
    splitType,
    splitValue:
      splitType !== "EQUAL"
        ? parseAmount(splitValues[userId] || "0")
        : undefined,
    shareAmount:
      splitType === "EXACT"
        ? parseAmount(splitValues[userId] || "0")
        : undefined,
  }));
}

// --- Form State & Reducer ---

export interface ExpenseFormState {
  description: string;
  amount: string;
  paidBy: number;
  categoryId: number | undefined;
  expenseDate: string;
  selectedMembers: number[];
  splitType: SplitType;
  splitValues: Record<number, string>;
}

export type ExpenseFormAction =
  | { type: "SET_DESCRIPTION"; payload: string }
  | { type: "SET_AMOUNT"; payload: string }
  | { type: "SET_PAID_BY"; payload: number }
  | { type: "SET_CATEGORY_ID"; payload: number | undefined }
  | { type: "SET_EXPENSE_DATE"; payload: string }
  | { type: "SET_SPLIT_TYPE"; payload: SplitType }
  | { type: "TOGGLE_MEMBER"; payload: number }
  | { type: "SET_SPLIT_VALUE"; payload: { userId: number; value: string } }
  | {
      type: "RESET_FORM";
      payload: { group: Group; currentUserId: number; expense?: Expense };
    };

/**
 * Creates the initial state for an expense form.
 */
export function createInitialExpenseFormState(
  group: Group,
  currentUserId: number,
  expense?: Expense,
): ExpenseFormState {
  if (expense) {
    const splitsMap: Record<number, string> = {};
    expense.splits?.forEach((s) => {
      splitsMap[s.userId] = s.shareAmount.toString();
    });

    // The backend does not return `splitType` on read: ExpenseDTO omits it (it exists only on the
    // Create/Update requests), and ExpenseSplit carries no type information either. So this always
    // resolved to "EQUAL" at runtime behind an unsound cast. Defaulting honestly instead of casting;
    // see the note in README Known issues.
    const inferredSplitType: SplitType = "EQUAL";

    return {
      description: expense.description || "",
      amount: expense.amount?.toString() || "",
      paidBy: expense.paidBy || currentUserId,
      categoryId: expense.categoryId,
      expenseDate:
        expense.expenseDate?.split("T")[0] ||
        new Date().toISOString().split("T")[0],
      selectedMembers:
        expense.splits?.map((s) => s.userId) || group.members.map((m) => m.userId),
      splitType: inferredSplitType,
      splitValues: inferredSplitType !== "EQUAL" ? splitsMap : {},
    };
  }

  return {
    description: "",
    amount: "",
    paidBy: currentUserId,
    categoryId: undefined,
    expenseDate: new Date().toISOString().split("T")[0],
    selectedMembers: group.members.map((m) => m.userId),
    splitType: "EQUAL",
    splitValues: {},
  };
}

/**
 * Pure reducer managing atomic state transitions for the expense form.
 */
export function expenseFormReducer(
  state: ExpenseFormState,
  action: ExpenseFormAction,
): ExpenseFormState {
  switch (action.type) {
    case "SET_DESCRIPTION":
      return { ...state, description: action.payload };
    case "SET_AMOUNT":
      return { ...state, amount: action.payload };
    case "SET_PAID_BY":
      return { ...state, paidBy: action.payload };
    case "SET_CATEGORY_ID":
      return { ...state, categoryId: action.payload };
    case "SET_EXPENSE_DATE":
      return { ...state, expenseDate: action.payload };
    case "SET_SPLIT_TYPE":
      return {
        ...state,
        splitType: action.payload,
        splitValues: {}, // Clear split values on split mode change
      };
    case "TOGGLE_MEMBER": {
      const userId = action.payload;
      const isSelected = state.selectedMembers.includes(userId);
      const nextMembers = isSelected
        ? state.selectedMembers.filter((id) => id !== userId)
        : [...state.selectedMembers, userId];

      const nextValues = { ...state.splitValues };
      if (isSelected && state.splitType !== "EQUAL") {
        delete nextValues[userId];
      }

      return {
        ...state,
        selectedMembers: nextMembers,
        splitValues: nextValues,
      };
    }
    case "SET_SPLIT_VALUE":
      return {
        ...state,
        splitValues: {
          ...state.splitValues,
          [action.payload.userId]: action.payload.value,
        },
      };
    case "RESET_FORM":
      return createInitialExpenseFormState(
        action.payload.group,
        action.payload.currentUserId,
        action.payload.expense,
      );
    default:
      return state;
  }
}

/**
 * Deep Expense Form Engine Hook.
 * Encapsulates form inputs, split mode state, validation, and request payload assembly.
 */
export function useExpenseForm(params: {
  group: Group;
  currentUserId: number;
  expense?: Expense;
}) {
  const { group, currentUserId, expense } = params;

  const [state, dispatch] = useReducer(
    expenseFormReducer,
    { group, currentUserId, expense },
    (init) =>
      createInitialExpenseFormState(
        init.group,
        init.currentUserId,
        init.expense,
      ),
  );

  const numAmount = useMemo(() => parseAmount(state.amount), [state.amount]);

  const splitFormValues: SplitFormValues = useMemo(
    () => ({
      splitType: state.splitType,
      amount: numAmount,
      splitValues: state.splitValues,
      selectedMembers: state.selectedMembers,
    }),
    [state.splitType, numAmount, state.splitValues, state.selectedMembers],
  );

  const validation: ValidationResult = useMemo(
    () => validateSplit(splitFormValues),
    [splitFormValues],
  );

  const sharePerPerson = useMemo(
    () => perShare(splitFormValues),
    [splitFormValues],
  );

  const isReadyToSubmit = useMemo(
    () =>
      Boolean(state.description) &&
      Boolean(state.amount) &&
      state.selectedMembers.length > 0 &&
      validation.isValid,
    [
      state.description,
      state.amount,
      state.selectedMembers.length,
      validation.isValid,
    ],
  );

  const getCreatePayload = useCallback((): CreateExpenseRequest => {
    return {
      description: state.description,
      amount: numAmount,
      paidBy: state.paidBy,
      categoryId: state.categoryId,
      expenseDate: state.expenseDate,
      splitType: state.splitType,
      splits: buildSplits(splitFormValues),
    };
  }, [state, numAmount, splitFormValues]);

  const getUpdatePayload = useCallback((): UpdateExpenseRequest => {
    return {
      description: state.description,
      amount: numAmount,
      paidBy: state.paidBy,
      categoryId: state.categoryId,
      expenseDate: state.expenseDate,
      splitType: state.splitType,
      splits: buildSplits(splitFormValues),
    };
  }, [state, numAmount, splitFormValues]);

  return {
    state,
    numAmount,
    validation,
    sharePerPerson,
    isReadyToSubmit,

    // Action dispatchers
    setDescription: (val: string) =>
      dispatch({ type: "SET_DESCRIPTION", payload: val }),
    setAmount: (val: string) =>
      dispatch({ type: "SET_AMOUNT", payload: val }),
    setPaidBy: (val: number) =>
      dispatch({ type: "SET_PAID_BY", payload: val }),
    setCategoryId: (val: number | undefined) =>
      dispatch({ type: "SET_CATEGORY_ID", payload: val }),
    setExpenseDate: (val: string) =>
      dispatch({ type: "SET_EXPENSE_DATE", payload: val }),
    setSplitType: (val: SplitType) =>
      dispatch({ type: "SET_SPLIT_TYPE", payload: val }),
    toggleMember: (userId: number) =>
      dispatch({ type: "TOGGLE_MEMBER", payload: userId }),
    setSplitValue: (userId: number, value: string) =>
      dispatch({ type: "SET_SPLIT_VALUE", payload: { userId, value } }),
    resetForm: () =>
      dispatch({
        type: "RESET_FORM",
        payload: { group, currentUserId, expense },
      }),

    getCreatePayload,
    getUpdatePayload,

    // UI Formatting Helpers
    formatters: {
      placeholder,
      unitPrefix,
      unitSuffix,
      splitTypeLabel,
    },
  };
}
