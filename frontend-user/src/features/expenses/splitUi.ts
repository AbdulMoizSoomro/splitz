import type { SplitType } from "../../types/expense";

/**
 * Per-strategy presentation facts used to render split inputs.
 * A single lookup map keeps every format decision next to its strategy — no
 * repeated switch over SplitType.
 */
interface SplitFormat {
  placeholder: string;
  prefix: string;
  suffix: string;
  label: string;
}

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
