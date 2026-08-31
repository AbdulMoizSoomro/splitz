import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode } from "react";
import { useDisplayName, useDisplayNames } from "./useDisplayName";
import { friendService } from "../features/users/friendService";
import { userService } from "../features/users/userService";

// ---------------------------------------------------------------------------
// Module mocks
// ---------------------------------------------------------------------------
vi.mock("../features/users/friendService");
vi.mock("../features/users/userService");
vi.mock("../store/authStore", () => ({
  useAuthStore: vi.fn(
    (
      selector: (s: {
        user: { id: string; username: string; email: string } | null;
      }) => unknown,
    ) =>
      selector({
        user: { id: "1", username: "alice", email: "alice@example.com" },
      }),
  ),
}));

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
const CURRENT_USER_ID = 1;

function makeWrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
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

const NON_FRIEND = {
  id: 3,
  username: "charlie",
  firstName: "Charlie",
  lastName: "Jones",
  email: "charlie@example.com",
};

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe("useDisplayName", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(userService.getUser).mockResolvedValue(null);
  });

  // -------------------------------------------------------------------------
  // Behavior 1: self → "You"
  // -------------------------------------------------------------------------
  it("returns 'You' for the current user's own ID", async () => {
    const { result } = renderHook(() => useDisplayName(CURRENT_USER_ID), {
      wrapper: makeWrapper(),
    });

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

    await waitFor(() => expect(result.current).toBe("Bob Smith"));
  });

  // -------------------------------------------------------------------------
  // Behavior 3: non-friend resolved via userService → "First Last"
  // -------------------------------------------------------------------------
  it("resolves non-friend users via userService", async () => {
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(userService.getUser).mockImplementation(async (id) => {
      if (Number(id) === 3) return NON_FRIEND;
      return null;
    });

    const { result } = renderHook(() => useDisplayName(NON_FRIEND.id), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => expect(result.current).toBe("Charlie Jones"));
  });

  // -------------------------------------------------------------------------
  // Behavior 4: unknown user → "User N"
  // -------------------------------------------------------------------------
  it("falls back to 'User N' when no match is found", async () => {
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(userService.getUser).mockResolvedValue(null);

    const { result } = renderHook(() => useDisplayName(999), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => expect(result.current).toBe("User 999"));
  });
});

// ---------------------------------------------------------------------------
// Bulk variant
// ---------------------------------------------------------------------------
describe("useDisplayNames", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(userService.getUser).mockResolvedValue(null);
  });

  // -------------------------------------------------------------------------
  // Behavior 5: bulk variant returns a complete map
  // -------------------------------------------------------------------------
  it("returns a complete userId→name map for a list of IDs", async () => {
    vi.mocked(friendService.getFriends).mockResolvedValue([FRIEND]);
    vi.mocked(userService.getUser).mockImplementation(async (id) => {
      if (Number(id) === 3) return NON_FRIEND;
      return null;
    });

    const { result } = renderHook(
      () =>
        useDisplayNames([
          CURRENT_USER_ID,
          FRIEND.id,
          NON_FRIEND.id,
          999,
        ]),
      { wrapper: makeWrapper() },
    );

    await waitFor(() => {
      expect(result.current).toEqual({
        [CURRENT_USER_ID]: "You",
        [FRIEND.id]: "Bob Smith",
        [NON_FRIEND.id]: "Charlie Jones",
        999: "User 999",
      });
    });
  });
});
