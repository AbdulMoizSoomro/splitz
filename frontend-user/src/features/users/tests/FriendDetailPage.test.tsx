import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import FriendDetailPage from "../FriendDetailPage";
import api, { expenseApi } from "../../../lib/axios";
import { useAuthStore } from "../../../store/authStore";
import { vi } from "vitest";

// Mock dependencies
vi.mock("../../../lib/axios", () => ({
  default: { get: vi.fn() },
  expenseApi: { get: vi.fn() },
}));

vi.mock("../../../store/authStore", () => ({
  useAuthStore: vi.fn(),
}));

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false } },
});

describe("FriendDetailPage - Unified Activity Feed", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (useAuthStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
      user: { id: "1", username: "testuser" },
    });
  });

  it("should display Shared Activity instead of Shared Expenses", async () => {
    // Mock responses
    (api.get as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: { id: 2, firstName: "Alice", lastName: "Smith", email: "alice@test.com", username: "alice" }
    }); // Friend
    
    (api.get as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ data: [] }); // Groups
    
    (api.get as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ data: { netBalance: 0 } }); // Balance

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/friends/2"]}>
          <Routes>
            <Route path="/friends/:id" element={<FriendDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Shared Activity")).toBeInTheDocument();
      expect(screen.queryByText("Shared Expenses")).not.toBeInTheDocument();
    });
  });

  it("should display friendship settlements in the unified list", async () => {
    (api.get as unknown as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.includes("/users/2")) {
        return Promise.resolve({ data: { id: 2, firstName: "Alice", lastName: "Smith", email: "alice@test.com", username: "alice" } });
      }
      if (url.includes("/groups")) {
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({ data: {} });
    });

    (expenseApi.get as unknown as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url.includes("/balances")) {
        return Promise.resolve({ data: { netBalance: -100 } });
      }
      if (url.includes("/settlements")) {
        return Promise.resolve({
          data: [{ id: 1, amount: 100, status: "PENDING", createdAt: new Date().toISOString(), payerId: 1, payeeId: 2 }]
        });
      }
      return Promise.resolve({ data: [] });
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/friends/2"]}>
          <Routes>
            <Route path="/friends/:id" element={<FriendDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/Pending/i)).toBeInTheDocument();
      expect(screen.getAllByText(/100.00/i).length).toBeGreaterThan(0);
    });
  });
});
