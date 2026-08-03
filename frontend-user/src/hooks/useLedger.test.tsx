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

const MEMBER_ROWS = [
  {
    userId: 1,
    username: "alice",
    email: "alice@example.com",
    firstName: "Alice",
    lastName: "Green",
    balance: 20,
  },
  {
    userId: 2,
    username: "bob",
    email: "bob@example.com",
    firstName: "Bob",
    lastName: "Smith",
    balance: -20,
  },
];

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
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 10,
      balances: MEMBER_ROWS,
      simplifiedDebts: [{ from: 2, fromUsername: "bob", to: 1, toUsername: "alice", amount: 20 }],
    });
  });

  it("detail: true — hides the group fan-out and derives the ledger", async () => {
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
        },
      ]);
    });

    expect(result.current.ledger?.position).toEqual({
      total: 50,
      groupTotal: 20,
      direct: 30,
    });
    expect(result.current.ledger?.memberNames[2]).toBe("Bob Smith");
    // The N+1 lives here, not in the consumer.
    expect(groupService.getBalances).toHaveBeenCalledWith(10);
  });

  it("detail: false exposes the overall position without the group fan-out", async () => {
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
    expect(groupService.getBalances).not.toHaveBeenCalled();
  });

  it("leaves the ledger empty for an anonymous viewer", async () => {
    mockUseAuthStore.mockReturnValue({ user: null });

    const { result } = renderHook(() => useLedger({ detail: true }), {
      wrapper: makeWrapper(),
    });

    expect(result.current.ledger).toBeUndefined();
    expect(groupService.getBalances).not.toHaveBeenCalled();
  });
});
