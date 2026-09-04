import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { FriendsSummaryBanner } from "./FriendsSummaryBanner";

describe("FriendsSummaryBanner", () => {
  it("renders total owed, total owe, and net balance correctly when positive net", () => {
    render(
      <FriendsSummaryBanner
        summary={{
          totalOwed: 150.5,
          totalOwe: 50.0,
          netBalance: 100.5,
        }}
      />,
    );

    expect(screen.getByText(/you are owed/i)).toBeInTheDocument();
    expect(screen.getByText("$150.50")).toBeInTheDocument();

    expect(screen.getByText(/you owe/i)).toBeInTheDocument();
    expect(screen.getByText("$50.00")).toBeInTheDocument();

    expect(screen.getByText(/net balance/i)).toBeInTheDocument();
    expect(screen.getByText("+$100.50")).toBeInTheDocument();
  });

  it("renders negative net balance correctly", () => {
    render(
      <FriendsSummaryBanner
        summary={{
          totalOwed: 20.0,
          totalOwe: 80.0,
          netBalance: -60.0,
        }}
      />,
    );

    expect(screen.getByText("-$60.00")).toBeInTheDocument();
  });

  it("renders settled zero net balance", () => {
    render(
      <FriendsSummaryBanner
        summary={{
          totalOwed: 0,
          totalOwe: 0,
          netBalance: 0,
        }}
      />,
    );

    const zeroElements = screen.getAllByText("$0.00");
    expect(zeroElements).toHaveLength(3);
  });
});
