import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import UserSearch from "./UserSearch";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

vi.mock("../../lib/axios");

describe("UserSearch", () => {
  it("renders search input", () => {
    render(
      <QueryClientProvider client={queryClient}>
        <UserSearch />
      </QueryClientProvider>,
    );
    expect(
      screen.getByPlaceholderText(/search by name or email/i),
    ).toBeInTheDocument();
  });

  it("updates input value on change", () => {
    render(
      <QueryClientProvider client={queryClient}>
        <UserSearch />
      </QueryClientProvider>,
    );
    const input = screen.getByPlaceholderText(
      /search by name or email/i,
    ) as HTMLInputElement;
    fireEvent.change(input, { target: { value: "john" } });
    expect(input.value).toBe("john");
  });
});
