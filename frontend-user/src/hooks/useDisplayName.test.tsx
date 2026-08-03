import { renderHook } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode } from "react";
import { useDisplayName, useDisplayNames } from "./useDisplayName";
import { friendService } from "../features/users/friendService";
import { groupService } from "../features/groups/groupService";

// ---------------------------------------------------------------------------
// Module mocks
// ---------------------------------------------------------------------------
vi.mock("../features/users/friendService");
vi.mock("../features/groups/groupService");
vi.mock("../store/authStore", () => ({
  useAuthStore: vi.fn((selector: (s: { user: { id: string; username: string; email: string } | null }) => unknown) =>
    selector({ user: { id: "1", username: "alice", email: "alice@example.com" } }),
  ),
}));

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
const CURRENT_USER_ID = 1;

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

// ---------------------------------------------------------------------------
// Shared mocked data
// ---------------------------------------------------------------------------
const FRIEND = {
  id: 2,
  username: "bob",
  firstName: "Bob",
  lastName: "Smith",
  email: "bob@example.com",
};

const GROUP_BALANCE_MEMBER = {
  userId: 3,
  username: "charlie",
  firstName: "Charlie",
  lastName: "Jones",
  email: "charlie@example.com",
  balance: 10,
};

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe("useDisplayName", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(groupService.getGroups).mockResolvedValue([]);
    vi.mocked(groupService.getUserBalances).mockResolvedValue({
      userId: 1,
      username: "alice",
      email: "alice@example.com",
      totalBalance: 0,
      groupBalances: [],
    });
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 10,
      balances: [],
      simplifiedDebts: [],
    });
  });

  // -------------------------------------------------------------------------
  // Behavior 1: self → "You"
  // -------------------------------------------------------------------------
  it("returns 'You' for the current user's own ID", async () => {
    const { result } = renderHook(() => useDisplayName(CURRENT_USER_ID), {
      wrapper: makeWrapper(),
    });

    // The hook resolves synchronously for self (no async lookup needed)
    expect(result.current).toBe("You");
  });

  // -------------------------------------------------------------------------
  // Behavior 2: known friend → "First Last"
  // -------------------------------------------------------------------------
  it("returns 'First Last' for a known friend", async () => {
    vi.mocked(friendService.getFriends).mockResolvedValue([FRIEND]);

    const { result } = renderHook(() => useDisplayName(FRIEND.id), {
      wrapper: makeWrapper(),
    });

    // Wait for friends query to resolve
    await vi.waitFor(() => expect(result.current).toBe("Bob Smith"));
  });

  // -------------------------------------------------------------------------
  // Behavior 3: group balance member (not a friend) → "First Last"
  // -------------------------------------------------------------------------
  it("falls back to group balance data for a non-friend group member", async () => {
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(groupService.getGroups).mockResolvedValue([
      {
        id: 10,
        name: "Trip",
        createdBy: 1,
        active: true,
        allowMembersToManageMembers: false,
        allowMembersToEditExpenses: false,
        createdAt: "",
        updatedAt: "",
        members: [
          { id: 100, userId: 1, role: "MEMBER", joinedAt: "" },
          { id: 101, userId: 3, role: "MEMBER", joinedAt: "" },
        ],
      },
    ]);
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 10,
      balances: [GROUP_BALANCE_MEMBER],
      simplifiedDebts: [],
    });

    const { result } = renderHook(() => useDisplayName(GROUP_BALANCE_MEMBER.userId), {
      wrapper: makeWrapper(),
    });

    await vi.waitFor(() => expect(result.current).toBe("Charlie Jones"));
  });

  // -------------------------------------------------------------------------
  // Behavior 4: unknown user → "User N"
  // -------------------------------------------------------------------------
  it("falls back to 'User N' when no match is found", async () => {
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(groupService.getGroups).mockResolvedValue([]);

    const { result } = renderHook(() => useDisplayName(999), {
      wrapper: makeWrapper(),
    });

    await vi.waitFor(() => expect(result.current).toBe("User 999"));
  });
});

// ---------------------------------------------------------------------------
// Bulk variant
// ---------------------------------------------------------------------------
describe("useDisplayNames", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(groupService.getGroups).mockResolvedValue([]);
    vi.mocked(groupService.getUserBalances).mockResolvedValue({
      userId: 1,
      username: "alice",
      email: "alice@example.com",
      totalBalance: 0,
      groupBalances: [],
    });
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 10,
      balances: [],
      simplifiedDebts: [],
    });
  });

  // -------------------------------------------------------------------------
  // Behavior 5: bulk variant returns a complete map
  // -------------------------------------------------------------------------
  it("returns a complete userId→name map for a list of IDs", async () => {
    vi.mocked(friendService.getFriends).mockResolvedValue([FRIEND]);
    vi.mocked(groupService.getGroups).mockResolvedValue([
      {
        id: 10,
        name: "Trip",
        createdBy: 1,
        active: true,
        allowMembersToManageMembers: false,
        allowMembersToEditExpenses: false,
        createdAt: "",
        updatedAt: "",
        members: [
          { id: 100, userId: 1, role: "MEMBER", joinedAt: "" },
          { id: 101, userId: 3, role: "MEMBER", joinedAt: "" },
        ],
      },
    ]);
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 10,
      balances: [GROUP_BALANCE_MEMBER],
      simplifiedDebts: [],
    });

    const { result } = renderHook(
      () => useDisplayNames([CURRENT_USER_ID, FRIEND.id, GROUP_BALANCE_MEMBER.userId, 999]),
      { wrapper: makeWrapper() },
    );

    await vi.waitFor(() => {
      expect(result.current).toEqual({
        [CURRENT_USER_ID]: "You",
        [FRIEND.id]: "Bob Smith",
        [GROUP_BALANCE_MEMBER.userId]: "Charlie Jones",
        999: "User 999",
      });
    });

    // Flush the ledger's trailing group-balance fan-out so its state update
    // lands inside act() and doesn't warn about an unwrapped update.
    await vi.waitFor(() => {
      expect(vi.mocked(groupService.getBalances)).toHaveBeenCalled();
    });
  });
});
