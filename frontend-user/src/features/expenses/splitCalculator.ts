import type { SplitType, SplitRequest } from "../../types/expense";

/**
 * The split state that travels together through every calculation.
 * Bundling the form into one value object keeps the interface small: callers
 * pass one cohesive input instead of a scattered (splitType, amount,
 * splitValues, selectedMembers) tuple.
 */
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


