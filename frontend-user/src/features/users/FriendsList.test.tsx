import { render } from "@testing-library/react";
import { describe, it, vi } from "vitest";
import FriendsList from "./FriendsList";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

vi.mock("../../lib/axios");

describe("FriendsList", () => {
  it("renders friends list header", () => {
    render(
      <QueryClientProvider client={queryClient}>
        <FriendsList />
      </QueryClientProvider>,
    );
    // Since it's loading initially, we might see the loader or empty state depending on mock
  });
});
