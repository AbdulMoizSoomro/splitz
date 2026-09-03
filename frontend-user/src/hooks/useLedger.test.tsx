import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode } from "react";
import { useLedger } from "./useLedger";
import { friendService } from "../features/users/friendService";
import { groupService } from "../features/groups/groupService";

// ---------------------------------------------------------------------------
// Module mocks
// ---------------------------------------------------------------------------
vi.mock("../features/users/friendService");
vi.mock("../features/groups/groupService");
const mockUseAuthStore = vi.fn();
vi.mock("../store/authStore", () => ({
  useAuthStore: (selector?: (s: { user: { id: string; username: string; email: string } | null }) => unknown) => {
    const state = mockUseAuthStore();
    return selector ? selector(state) : state;
  },
}));

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

describe("useLedger", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuthStore.mockReturnValue({ user: { id: "1", username: "alice" } });
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(groupService.getGroups).mockResolvedValue([]);
    vi.mocked(groupService.getUserBalances).mockResolvedValue({
      userId: 1,
      username: "alice",
      email: "alice@example.com",
      totalBalance: 50,
      groupBalances: [{ groupId: 10, groupName: "Trip", balance: 20 }],
    });
    vi.mocked(groupService.getCounterparties).mockResolvedValue([
      {
        userId: 2,
        username: "bob",
        firstName: "Bob",
        lastName: "Smith",
        email: "bob@example.com",
        balance: 20,
        groups: [{ id: 10, name: "Trip" }],
      },
    ]);
  });

  it("detail: true — queries counterparties in a single request and derives the ledger", async () => {
    const { result } = renderHook(() => useLedger({ detail: true }), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => {
      expect(result.current.ledger?.counterparties).toEqual([
        {
          userId: 2,
          name: "Bob Smith",
          balance: 20,
          groups: [{ id: 10, name: "Trip" }],
          username: "bob",
          firstName: "Bob",
          lastName: "Smith",
          email: "bob@example.com",
        },
      ]);
    });

    expect(result.current.ledger?.position).toEqual({
      total: 50,
      groupTotal: 20,
      direct: 30,
    });
    expect(result.current.ledger?.memberNames[2]).toBe("Bob Smith");
    expect(groupService.getCounterparties).toHaveBeenCalledWith(1);
    expect(groupService.getBalances).not.toHaveBeenCalled();
  });

  it("detail: false exposes the overall position without fetching counterparties", async () => {
    const { result } = renderHook(() => useLedger(), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => {
      expect(result.current.ledger?.position.total).toBe(50);
    });

    expect(result.current.ledger?.counterparties).toEqual([]);
    expect(result.current.ledger?.groupPositions).toEqual([
      { groupId: 10, groupName: "Trip", balance: 20 },
    ]);
    expect(result.current.friends).toBeUndefined();
    expect(groupService.getCounterparties).not.toHaveBeenCalled();
  });

  it("leaves the ledger empty for an anonymous viewer", async () => {
    mockUseAuthStore.mockReturnValue({ user: null });

    const { result } = renderHook(() => useLedger({ detail: true }), {
      wrapper: makeWrapper(),
    });

    expect(result.current.ledger).toBeUndefined();
    expect(groupService.getCounterparties).not.toHaveBeenCalled();
  });
});
