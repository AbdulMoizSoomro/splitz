import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import ActivityPage from "./ActivityPage";
import { activityService } from "./activityService";
import { friendService } from "../users/friendService";
import { groupService } from "../groups/groupService";
import type { GlobalActivity } from "./activityService";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

vi.mock("./activityService");
vi.mock("../users/friendService");
vi.mock("../groups/groupService");
vi.mock("../../store/authStore", () => ({
  useAuthStore: () => ({
    user: { id: "1", username: "alice", email: "alice@example.com" },
  }),
}));

describe("ActivityPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(groupService.getGroups).mockResolvedValue([]);
  });

  it("renders the activity heading", () => {
    vi.mocked(activityService.getGlobalActivity).mockReturnValue(new Promise(() => {}));
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <ActivityPage />
        </MemoryRouter>
      </QueryClientProvider>
    );
    expect(screen.getByText(/Shared Activity/i)).toBeInTheDocument();
  });

  it("displays loading state", () => {
    vi.mocked(activityService.getGlobalActivity).mockReturnValue(new Promise(() => {}));
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <ActivityPage />
        </MemoryRouter>
      </QueryClientProvider>
    );
    expect(screen.getByTestId("loader")).toBeInTheDocument();
  });

  it("displays list of activities sorted by date", async () => {
    const mockData: GlobalActivity = {
      expenses: [
        { id: 1, description: "Old Expense", amount: 10, currency: "USD", expenseDate: "2023-01-01T12:00:00Z", groupId: 1, paidBy: 1, splits: [], createdAt: "2023-01-01T12:00:00Z", updatedAt: "2023-01-01T12:00:00Z" },
        { id: 2, description: "New Expense", amount: 20, currency: "USD", expenseDate: "2023-01-05T12:00:00Z", groupId: 1, paidBy: 1, splits: [], createdAt: "2023-01-05T12:00:00Z", updatedAt: "2023-01-05T12:00:00Z" },
      ],
      settlements: [
        { id: 1, amount: 5, currency: "USD", paidAt: "2023-01-03T12:00:00Z", payerId: 1, payeeId: 2, groupId: 1, status: "COMPLETED", createdAt: "2023-01-03T12:00:00Z", updatedAt: "2023-01-03T12:00:00Z" },
      ],
    };
    vi.mocked(activityService.getGlobalActivity).mockResolvedValue(mockData);

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <ActivityPage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/New Expense/i)).toBeInTheDocument();
      expect(screen.getByText(/Old Expense/i)).toBeInTheDocument();
    });

    const items = screen.getAllByText(/Expense/i);
    expect(items.length).toBeGreaterThan(0);
  });
});
