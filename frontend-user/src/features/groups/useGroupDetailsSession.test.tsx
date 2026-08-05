import { renderHook, waitFor, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { useGroupDetailsSession } from "./useGroupDetailsSession";
import { groupService } from "./groupService";
import { friendService } from "../users/friendService";
import { settlementService } from "../balances/settlementService";
import { expenseService } from "../expenses/expenseService";
import { categoryService } from "../expenses/categoryService";
import type { Group } from "../../types/group";
import type { ReactNode } from "react";

vi.mock("./groupService");
vi.mock("../users/friendService");
vi.mock("../balances/settlementService");
vi.mock("../expenses/expenseService");
vi.mock("../expenses/categoryService");
const mockUseAuthStore = vi.fn();
vi.mock("../../store/authStore", () => ({
  useAuthStore: (selector?: (s: { user: { id: string; username: string } | null }) => unknown) => {
    const state = mockUseAuthStore();
    return selector ? selector(state) : state;
  },
}));

const mockGroup: Group = {
  id: 1,
  name: "Test Group",
  description: "Test Description",
  members: [
    { id: 1, userId: 1, role: "ADMIN", joinedAt: "2025-01-01T10:00:00Z" },
  ],
  createdBy: 1,
  active: true,
  allowMembersToManageMembers: true,
  allowMembersToEditExpenses: true,
  createdAt: "2025-01-01T10:00:00Z",
  updatedAt: "2025-01-01T10:00:00Z",
};

describe("useGroupDetailsSession", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0, staleTime: 0 },
      },
    });
    vi.clearAllMocks();
    mockUseAuthStore.mockReturnValue({
      user: { id: "1", username: "testuser" },
    });
    vi.mocked(groupService.getGroup).mockResolvedValue(mockGroup);
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 1,
      balances: [],
      simplifiedDebts: [],
    });
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(settlementService.getSettlementsByGroup).mockResolvedValue([]);
    vi.mocked(expenseService.getGroupExpenses).mockResolvedValue([]);
    vi.mocked(categoryService.getCategories).mockResolvedValue([]);
  });

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/groups/1"]}>
        <Routes>
          <Route path="/groups/:id" element={<>{children}</>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

  it("fetches group session data and calculates derived governance", async () => {
    const { result } = renderHook(() => useGroupDetailsSession(), { wrapper });

    await waitFor(() => {
      expect(result.current.group).toEqual(mockGroup);
      expect(result.current.governance.currentUserRole).toBe("OWNER");
    });
  });

  it("manages modal toggle state", async () => {
    const { result } = renderHook(() => useGroupDetailsSession(), { wrapper });

    await waitFor(() => {
      expect(result.current.isLeaveModalOpen).toBe(false);
    });

    act(() => {
      result.current.setIsLeaveModalOpen(true);
    });
    expect(result.current.isLeaveModalOpen).toBe(true);
  });
});
