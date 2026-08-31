import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useGroupBalancesData } from "./useGroupBalancesData";
import { groupService } from "./groupService";
import { settlementService } from "../balances/settlementService";
import type { Group } from "../../types/group";
import type { ReactNode } from "react";

vi.mock("./groupService");
vi.mock("../balances/settlementService");

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

describe("useGroupBalancesData", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
    });
    vi.clearAllMocks();
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 1,
      balances: [
        {
          userId: 1,
          balance: 50,
          totalPaid: 100,
          totalOwed: 50,
          netBalance: 50,
        },
      ],
      simplifiedDebts: [],
    });
    vi.mocked(settlementService.getSettlementsByGroup).mockResolvedValue([]);
  });

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it("fetches group balances and calculates current user balance and governance", async () => {
    const { result } = renderHook(
      () => useGroupBalancesData(1, 1, mockGroup),
      { wrapper },
    );

    await waitFor(() => {
      expect(result.current.currentUserBalance).toBe(50);
      expect(result.current.governance.currentUserRole).toBe("OWNER");
    });
  });
});
