import type { SplitType, SplitRequest } from "../../types/expense";

export interface ValidationResult {
  isValid: boolean;
  message?: string;
  error?: string;
}

export function validateSplit(
  splitType: SplitType,
  totalSplitValue: number,
  numAmount: number,
): ValidationResult {
  switch (splitType) {
    case "EQUAL":
      return { isValid: true };
    case "EXACT": {
      const isExactValid = Math.abs(totalSplitValue - numAmount) < 0.01;
      return {
        isValid: isExactValid,
        message: isExactValid
          ? "Fully allocated"
          : `Remaining: $${(numAmount - totalSplitValue).toFixed(2)}`,
        error:
          !isExactValid && numAmount > 0
            ? `Total must equal $${numAmount}`
            : undefined,
      };
    }
    case "PERCENTAGE": {
      const isPercentValid = Math.abs(totalSplitValue - 100) < 0.01;
      return {
        isValid: isPercentValid,
        message: isPercentValid
          ? "100% allocated"
          : `Total: ${totalSplitValue.toFixed(1)}%`,
        error: !isPercentValid ? "Total must equal 100%" : undefined,
      };
    }
    case "SHARES": {
      const hasShares = totalSplitValue > 0;
      return {
        isValid: hasShares,
        message: `Total shares: ${totalSplitValue}`,
        error: !hasShares ? "Total shares must be greater than 0" : undefined,
      };
    }
    case "ADJUSTMENT": {
      const isAdjValid = Math.abs(totalSplitValue) < 0.01;
      return {
        isValid: isAdjValid,
        message: isAdjValid
          ? "Adjustments balanced"
          : `Offset: ${totalSplitValue > 0 ? "+" : ""}$${totalSplitValue.toFixed(2)}`,
        error: !isAdjValid ? "Adjustments must sum to $0.00" : undefined,
      };
    }
    default:
      return { isValid: true };
  }
}

export function perShare(numAmount: number, memberCount: number): string {
  return numAmount && memberCount > 0
    ? (numAmount / memberCount).toFixed(2)
    : "0.00";
}

export function parseAmount(amount: string): number {
  return parseFloat(amount) || 0;
}

export function totalSplitValue(
  splitValues: Record<number, string>,
): number {
  return Object.values(splitValues).reduce(
    (sum, val) => sum + parseAmount(val),
    0,
  );
}

export function placeholder(splitType: SplitType): string {
  switch (splitType) {
    case "EXACT":
      return "0.00";
    case "PERCENTAGE":
      return "0";
    case "SHARES":
      return "1";
    case "ADJUSTMENT":
      return "0.00";
    default:
      return "";
  }
}

export function unitPrefix(splitType: SplitType): string {
  return splitType === "EXACT" || splitType === "ADJUSTMENT" ? "$" : "";
}

export function unitSuffix(splitType: SplitType): string {
  if (splitType === "PERCENTAGE") return "%";
  if (splitType === "SHARES") return " shares";
  return "";
}

export function splitTypeLabel(type: SplitType): string {
  return type === "ADJUSTMENT" ? "Fixed Adjustment" : type.toLowerCase();
}

export function buildSplits(
  selectedMembers: number[],
  splitType: SplitType,
  splitValues: Record<number, string>,
): SplitRequest[] {
  return selectedMembers.map((userId) => ({
    userId,
    splitType,
    splitValue:
      splitType !== "EQUAL" ? parseAmount(splitValues[userId] || "0") : undefined,
    shareAmount:
      splitType === "EXACT" ? parseAmount(splitValues[userId] || "0") : undefined,
  }));
}

