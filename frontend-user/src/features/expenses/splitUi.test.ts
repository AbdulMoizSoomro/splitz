import { describe, it, expect } from "vitest";
import {
  placeholder,
  unitPrefix,
  unitSuffix,
  splitTypeLabel,
} from "./splitUi";

describe("splitUi — placeholder", () => {
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

describe("splitUi — unitPrefix / unitSuffix", () => {
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

describe("splitUi — splitTypeLabel", () => {
  it("returns 'Fixed Adjustment' for ADJUSTMENT", () => {
    expect(splitTypeLabel("ADJUSTMENT")).toBe("Fixed Adjustment");
  });
  it("returns the lowercased type for others", () => {
    expect(splitTypeLabel("EXACT")).toBe("exact");
    expect(splitTypeLabel("PERCENTAGE")).toBe("percentage");
  });
});
