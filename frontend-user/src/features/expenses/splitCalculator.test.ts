import { describe, it, expect } from "vitest";
import {
  validateSplit,
  perShare,
  parseAmount,
  buildSplits,
  type SplitFormValues,
} from "./splitCalculator";

/** Builds a valid split form, overriding fields per test. */
function form(over: Partial<SplitFormValues> = {}): SplitFormValues {
  return {
    splitType: "EXACT",
    amount: 30,
    splitValues: { 1: "10", 2: "10", 3: "10" },
    selectedMembers: [1, 2, 3],
    ...over,
  };
}

describe("splitCalculator — validateSplit", () => {
  it("marks an EXACT split valid when values sum to the amount", () => {
    const result = validateSplit(form());
    expect(result.isValid).toBe(true);
    expect(result.message).toBe("Fully allocated");
    expect(result.error).toBeUndefined();
  });

  it("marks an EXACT split invalid with a remaining message when under-allocated", () => {
    const result = validateSplit(
      form({ splitValues: { 1: "10", 2: "10", 3: "0" } }),
    );
    expect(result.isValid).toBe(false);
    expect(result.message).toBe("Remaining: $10.00");
    expect(result.error).toBe("Total must equal $30");
  });

  it("marks a PERCENTAGE split valid when values sum to 100", () => {
    const result = validateSplit(
      form({ splitType: "PERCENTAGE", splitValues: { 1: "50", 2: "50" } }),
    );
    expect(result.isValid).toBe(true);
    expect(result.message).toBe("100% allocated");
  });

  it("marks a PERCENTAGE split invalid when values do not sum to 100", () => {
    const result = validateSplit(
      form({ splitType: "PERCENTAGE", splitValues: { 1: "40", 2: "50" } }),
    );
    expect(result.isValid).toBe(false);
    expect(result.error).toBe("Total must equal 100%");
  });

  it("marks a SHARES split valid when total shares are positive", () => {
    const result = validateSplit(
      form({ splitType: "SHARES", splitValues: { 1: "1", 2: "3" } }),
    );
    expect(result.isValid).toBe(true);
    expect(result.message).toBe("Total shares: 4");
  });

  it("marks a SHARES split invalid when total shares are zero", () => {
    const result = validateSplit(
      form({ splitType: "SHARES", splitValues: { 1: "0", 2: "0" } }),
    );
    expect(result.isValid).toBe(false);
    expect(result.error).toBe("Total shares must be greater than 0");
  });

  it("marks an ADJUSTMENT split valid when values sum to zero", () => {
    const result = validateSplit(
      form({ splitType: "ADJUSTMENT", splitValues: { 1: "5", 2: "-5" } }),
    );
    expect(result.isValid).toBe(true);
    expect(result.message).toBe("Adjustments balanced");
  });

  it("marks an ADJUSTMENT split invalid with an offset when non-zero", () => {
    const result = validateSplit(
      form({ splitType: "ADJUSTMENT", splitValues: { 1: "5", 2: "0" } }),
    );
    expect(result.isValid).toBe(false);
    expect(result.error).toBe("Adjustments must sum to $0.00");
  });

  it("always marks an EQUAL split valid", () => {
    const result = validateSplit(form({ splitType: "EQUAL" }));
    expect(result.isValid).toBe(true);
  });
});

describe("splitCalculator — perShare", () => {
  it("divides the amount evenly among members", () => {
    expect(perShare(form())).toBe("10.00");
  });

  it("returns 0.00 when there are no members", () => {
    expect(perShare(form({ selectedMembers: [] }))).toBe("0.00");
  });

  it("returns 0.00 when the amount is zero", () => {
    expect(perShare(form({ amount: 0 }))).toBe("0.00");
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

describe("splitCalculator — buildSplits", () => {
  it("builds EXACT splits with splitValue and shareAmount per member", () => {
    const result = buildSplits(form());
    expect(result).toEqual([
      { userId: 1, splitType: "EXACT", splitValue: 10, shareAmount: 10 },
      { userId: 2, splitType: "EXACT", splitValue: 10, shareAmount: 10 },
      { userId: 3, splitType: "EXACT", splitValue: 10, shareAmount: 10 },
    ]);
  });

  it("builds EQUAL splits with undefined splitValue and shareAmount", () => {
    const result = buildSplits(form({ splitType: "EQUAL" }));
    expect(result).toEqual([
      { userId: 1, splitType: "EQUAL", splitValue: undefined, shareAmount: undefined },
      { userId: 2, splitType: "EQUAL", splitValue: undefined, shareAmount: undefined },
      { userId: 3, splitType: "EQUAL", splitValue: undefined, shareAmount: undefined },
    ]);
  });

  it("treats missing split values as zero for non-EQUAL splits", () => {
    const result = buildSplits(
      form({ splitType: "PERCENTAGE", selectedMembers: [1], splitValues: {} }),
    );
    expect(result).toEqual([
      { userId: 1, splitType: "PERCENTAGE", splitValue: 0, shareAmount: undefined },
    ]);
  });
});
