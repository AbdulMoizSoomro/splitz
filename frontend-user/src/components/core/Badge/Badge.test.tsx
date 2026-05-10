import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import Badge from "./Badge";

describe("Badge", () => {
  it("renders children correctly", () => {
    render(<Badge>Test Badge</Badge>);
    expect(screen.getByText("Test Badge")).toBeInTheDocument();
  });

  it("applies variant classes correctly", () => {
    const { container } = render(<Badge variant="owner">Owner</Badge>);
    // Owner badge should have a distinct style, e.g., purple or gold
    expect(container.firstChild).toHaveClass("bg-purple-100");
  });
});
