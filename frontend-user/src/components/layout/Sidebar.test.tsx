import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import Sidebar from "./Sidebar";

describe("Sidebar", () => {
  it("includes a link to Activity", () => {
    render(
      <MemoryRouter>
        <Sidebar isOpen={true} onClose={() => {}} />
      </MemoryRouter>
    );
    const activityLink = screen.getByRole("link", { name: /Activity/i });
    expect(activityLink).toBeInTheDocument();
    expect(activityLink).toHaveAttribute("href", "/activity");
  });
});
