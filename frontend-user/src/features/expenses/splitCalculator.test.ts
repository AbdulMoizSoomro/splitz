import { describe, it, expect } from "vitest";
import {
  validateSplit,
  perShare,
  parseAmount,
  totalSplitValue,
  placeholder,
  unitPrefix,
  unitSuffix,
  splitTypeLabel,
  buildSplits,
} from "./splitCalculator";

describe("splitCalculator — validateSplit", () => {
  it("marks an EXACT split valid when values sum to the amount", () => {
    const result = validateSplit("EXACT", 30, 30);
    expect(result.isValid).toBe(true);
    expect(result.message).toBe("Fully allocated");
    expect(result.error).toBeUndefined();
  });

  it("marks an EXACT split invalid with a remaining message when under-allocated", () => {
    const result = validateSplit("EXACT", 20, 30);
    expect(result.isValid).toBe(false);
    expect(result.message).toBe("Remaining: $10.00");
    expect(result.error).toBe("Total must equal $30");
  });

  it("marks a PERCENTAGE split valid when values sum to 100", () => {
    const result = validateSplit("PERCENTAGE", 100, 50);
    expect(result.isValid).toBe(true);
    expect(result.message).toBe("100% allocated");
  });

  it("marks a PERCENTAGE split invalid when values do not sum to 100", () => {
    const result = validateSplit("PERCENTAGE", 90, 50);
    expect(result.isValid).toBe(false);
    expect(result.error).toBe("Total must equal 100%");
  });

  it("marks a SHARES split valid when total shares are positive", () => {
    const result = validateSplit("SHARES", 4, 50);
    expect(result.isValid).toBe(true);
    expect(result.message).toBe("Total shares: 4");
  });

  it("marks a SHARES split invalid when total shares are zero", () => {
    const result = validateSplit("SHARES", 0, 50);
    expect(result.isValid).toBe(false);
    expect(result.error).toBe("Total shares must be greater than 0");
  });

  it("marks an ADJUSTMENT split valid when values sum to zero", () => {
    const result = validateSplit("ADJUSTMENT", 0, 50);
    expect(result.isValid).toBe(true);
    expect(result.message).toBe("Adjustments balanced");
  });

  it("marks an ADJUSTMENT split invalid with an offset when non-zero", () => {
    const result = validateSplit("ADJUSTMENT", 5, 50);
    expect(result.isValid).toBe(false);
    expect(result.error).toBe("Adjustments must sum to $0.00");
  });

  it("always marks an EQUAL split valid", () => {
    const result = validateSplit("EQUAL", 0, 30);
    expect(result.isValid).toBe(true);
  });
});

describe("splitCalculator — perShare", () => {
  it("divides the amount evenly among members", () => {
    expect(perShare(30, 3)).toBe("10.00");
  });

  it("returns 0.00 when there are no members", () => {
    expect(perShare(30, 0)).toBe("0.00");
  });

  it("returns 0.00 when the amount is zero", () => {
    expect(perShare(0, 3)).toBe("0.00");
  });
});

describe("splitCalculator — parseAmount", () => {
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

describe("splitCalculator — totalSplitValue", () => {
  it("sums all numeric values", () => {
    expect(totalSplitValue({ 1: "10", 2: "20.5", 3: "abc" })).toBe(30.5);
  });

  it("returns 0 for an empty map", () => {
    expect(totalSplitValue({})).toBe(0);
  });
});

describe("splitCalculator — placeholder", () => {
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

describe("splitCalculator — unitPrefix / unitSuffix", () => {
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

describe("splitCalculator — splitTypeLabel", () => {
  it("returns 'Fixed Adjustment' for ADJUSTMENT", () => {
    expect(splitTypeLabel("ADJUSTMENT")).toBe("Fixed Adjustment");
  });
  it("returns the lowercased type for others", () => {
    expect(splitTypeLabel("EXACT")).toBe("exact");
    expect(splitTypeLabel("PERCENTAGE")).toBe("percentage");
  });
});

describe("splitCalculator — buildSplits", () => {
  it("builds EXACT splits with splitValue and shareAmount per member", () => {
    const result = buildSplits([1, 2, 3], "EXACT", { 1: "10", 2: "10", 3: "10" });
    expect(result).toEqual([
      { userId: 1, splitType: "EXACT", splitValue: 10, shareAmount: 10 },
      { userId: 2, splitType: "EXACT", splitValue: 10, shareAmount: 10 },
      { userId: 3, splitType: "EXACT", splitValue: 10, shareAmount: 10 },
    ]);
  });

  it("builds EQUAL splits with undefined splitValue and shareAmount", () => {
    const result = buildSplits([1, 2], "EQUAL", {});
    expect(result).toEqual([
      { userId: 1, splitType: "EQUAL", splitValue: undefined, shareAmount: undefined },
      { userId: 2, splitType: "EQUAL", splitValue: undefined, shareAmount: undefined },
    ]);
  });

  it("treats missing split values as zero for non-EQUAL splits", () => {
    const result = buildSplits([1], "PERCENTAGE", {});
    expect(result).toEqual([
      { userId: 1, splitType: "PERCENTAGE", splitValue: 0, shareAmount: undefined },
    ]);
  });
});


