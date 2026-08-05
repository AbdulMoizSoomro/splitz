import { renderHook, waitFor, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { useFriendDetailSession } from "../useFriendDetailSession";
import { useInterpersonalFriend } from "../interpersonal";
import type { ReactNode } from "react";

vi.mock("../interpersonal");
const mockUseAuthStore = vi.fn();
vi.mock("../../../store/authStore", () => ({
  useAuthStore: (selector?: (s: { user: { id: string; username: string; email: string } | null }) => unknown) => {
    const state = mockUseAuthStore();
    return selector ? selector(state) : state;
  },
}));

const mockFriend = {
  id: 2,
  username: "alice",
  email: "alice@test.com",
  firstName: "Alice",
  lastName: "Smith",
};

describe("useFriendDetailSession", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0, staleTime: 0 },
      },
    });
    vi.clearAllMocks();
    mockUseAuthStore.mockReturnValue({
      user: { id: "1", username: "testuser", email: "test@user.com" },
    });
    vi.mocked(useInterpersonalFriend).mockReturnValue({
      friend: mockFriend,
      relationshipStatus: "CONFIRMED_FRIEND",
      netBalance: 50,
      directBalance: 20,
      groupBalances: [{ groupId: 1, balance: 30 }],
      sharedGroups: [{ id: 1, name: "Ski Trip", members: [] } as any],
      activityFeed: [],
      isLoading: false,
      mutations: {
        sendFriendRequest: { mutate: vi.fn(), isPending: false } as any,
        cancelFriendRequest: { mutate: vi.fn(), isPending: false } as any,
        confirmSettlement: { mutate: vi.fn(), isPending: false } as any,
        updateSettlement: { mutate: vi.fn(), isPending: false } as any,
      },
    });
  });

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/friends/2"]}>
        <Routes>
          <Route path="/friends/:id" element={<>{children}</>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

  it("fetches friend detail session data and maps group names", async () => {
    const { result } = renderHook(() => useFriendDetailSession(), { wrapper });

    await waitFor(() => {
      expect(result.current.friend).toEqual(mockFriend);
      expect(result.current.isConfirmedFriend).toBe(true);
      expect(result.current.groupNameMap[1]).toBe("Ski Trip");
      expect(result.current.groupBalancesMap[1]).toBe(30);
    });
  });

  it("manages modal toggle state", async () => {
    const { result } = renderHook(() => useFriendDetailSession(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSettlementModalOpen).toBe(false);
    });

    act(() => {
      result.current.setIsSettlementModalOpen(true);
    });
    expect(result.current.isSettlementModalOpen).toBe(true);
  });
});
