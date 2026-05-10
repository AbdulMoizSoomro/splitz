import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import ActivityPage from "./ActivityPage";
import { activityService } from "./activityService";
import type { GlobalActivity } from "./activityService";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

vi.mock("./activityService");

describe("ActivityPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();
  });

  it("renders the activity heading", () => {
    vi.mocked(activityService.getGlobalActivity).mockReturnValue(new Promise(() => {}));
    render(
      <QueryClientProvider client={queryClient}>
        <ActivityPage />
      </QueryClientProvider>
    );
    expect(screen.getByText(/Shared Activity/i)).toBeInTheDocument();
  });

  it("displays loading state", () => {
    vi.mocked(activityService.getGlobalActivity).mockReturnValue(new Promise(() => {}));
    render(
      <QueryClientProvider client={queryClient}>
        <ActivityPage />
      </QueryClientProvider>
    );
    expect(screen.getByTestId("loader")).toBeInTheDocument();
  });

  it("displays list of activities sorted by date", async () => {
    const mockData: GlobalActivity = {
      expenses: [
        { id: 1, description: "Old Expense", amount: 10, currency: "USD", expenseDate: "2023-01-01", groupId: 1, paidBy: 1, splits: [], createdAt: "2023-01-01", updatedAt: "2023-01-01" },
        { id: 2, description: "New Expense", amount: 20, currency: "USD", expenseDate: "2023-01-05", groupId: 1, paidBy: 1, splits: [], createdAt: "2023-01-05", updatedAt: "2023-01-05" },
      ],
      settlements: [
        { id: 1, amount: 5, currency: "USD", paidAt: "2023-01-03", fromUserId: 1, toUserId: 2, groupId: 1, status: "PAID", createdAt: "2023-01-03", updatedAt: "2023-01-03" },
      ],
    };
    vi.mocked(activityService.getGlobalActivity).mockResolvedValue(mockData);

    render(
      <QueryClientProvider client={queryClient}>
        <ActivityPage />
      </QueryClientProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("New Expense")).toBeInTheDocument();
      expect(screen.getByText("Settlement")).toBeInTheDocument();
      expect(screen.getByText("Old Expense")).toBeInTheDocument();
    });

    // Check chronological order (simplified check via screen order if possible, or just presence for now)
    const items = screen.getAllByRole("heading", { level: 3 });
    expect(items[0].textContent).toBe("New Expense");
    expect(items[1].textContent).toBe("Settlement");
    expect(items[2].textContent).toBe("Old Expense");
  });
});
