import { renderHook, waitFor, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { useGroupMembership } from "./useGroupMembership";
import { groupService } from "./groupService";
import { friendService } from "../users/friendService";
import type { Group } from "../../types/group";
import type { ReactNode } from "react";

vi.mock("./groupService");
vi.mock("../users/friendService");

const mockGroup: Group = {
  id: 1,
  name: "Test Group",
  description: "Test Description",
  members: [
    { id: 1, userId: 1, role: "ADMIN", joinedAt: "2025-01-01T10:00:00Z" },
    { id: 2, userId: 2, role: "MEMBER", joinedAt: "2025-01-01T10:00:00Z" },
  ],
  createdBy: 1,
  active: true,
  allowMembersToManageMembers: true,
  allowMembersToEditExpenses: true,
  createdAt: "2025-01-01T10:00:00Z",
  updatedAt: "2025-01-01T10:00:00Z",
};

describe("useGroupMembership", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
    });
    vi.clearAllMocks();
    vi.mocked(groupService.getGroup).mockResolvedValue(mockGroup);
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
  });

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );

  it("fetches group data and tracks member display IDs", async () => {
    const { result } = renderHook(() => useGroupMembership(1, 1), { wrapper });

    await waitFor(() => {
      expect(result.current.group).toEqual(mockGroup);
      expect(result.current.isGroupLoading).toBe(false);
    });
  });

  it("manages membership modals", () => {
    const { result } = renderHook(() => useGroupMembership(1, 1), { wrapper });

    act(() => {
      result.current.setIsAddMemberModalOpen(true);
    });
    expect(result.current.isAddMemberModalOpen).toBe(true);

    act(() => {
      result.current.setIsLeaveModalOpen(true);
    });
    expect(result.current.isLeaveModalOpen).toBe(true);
  });
});
