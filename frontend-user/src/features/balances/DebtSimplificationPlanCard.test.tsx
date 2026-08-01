import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { DebtSimplificationPlanCard } from "./DebtSimplificationPlanCard";
import { simplificationService } from "./simplificationService";
import type { DebtSimplificationPlan } from "../../types/simplification";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

vi.mock("./simplificationService");

describe("DebtSimplificationPlanCard", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0, staleTime: 0 },
      },
    });
  });

  afterEach(() => {
    cleanup();
  });

  const renderComponent = (props: { groupId: number; currentUserId: number; onSettleDebt?: any }) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <DebtSimplificationPlanCard {...props} />
      </QueryClientProvider>,
    );
  };

  it("renders suggested settlement plan metrics and transactions", async () => {
    const mockPlan: DebtSimplificationPlan = {
      groupId: 1,
      scope: "INTRA_GROUP",
      status: "COMPLETED",
      simplificationEnabled: true,
      originalTransactionCount: 5,
      simplifiedTransactionCount: 2,
      totalDebtVolume: 150.0,
      optedOutUserIds: [],
      transactions: [
        {
          fromUserId: 1,
          fromUsername: "Alice",
          toUserId: 2,
          toUsername: "Bob",
          amount: 50.0,
          status: "SUGGESTED",
        },
        {
          fromUserId: 3,
          fromUsername: "Charlie",
          toUserId: 2,
          toUsername: "Bob",
          amount: 100.0,
          status: "SUGGESTED",
        },
      ],
    };

    vi.mocked(simplificationService.getSimplificationPlan).mockResolvedValueOnce(mockPlan);

    renderComponent({ groupId: 1, currentUserId: 99 });

    expect(await screen.findByText("Suggested Settlement Plan")).toBeInTheDocument();
    expect(screen.getByText("Intra-Group Netting")).toBeInTheDocument();
    expect(screen.getByText("60%")).toBeInTheDocument(); // (5 - 2)/5 * 100
    expect(screen.getByText("$150.00")).toBeInTheDocument();
    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getAllByText("Bob").length).toBeGreaterThan(0);
    expect(screen.getByText("Charlie")).toBeInTheDocument();
  });

  it("calls onSettleDebt when current user clicks Settle on their suggested debt", async () => {
    const mockPlan: DebtSimplificationPlan = {
      groupId: 1,
      scope: "CROSS_GROUP",
      status: "COMPLETED",
      simplificationEnabled: true,
      originalTransactionCount: 3,
      simplifiedTransactionCount: 1,
      totalDebtVolume: 75.0,
      optedOutUserIds: [],
      transactions: [
        {
          fromUserId: 1,
          fromUsername: "Alice",
          toUserId: 3,
          toUsername: "Charlie",
          amount: 75.0,
          status: "SUGGESTED",
        },
      ],
    };

    vi.mocked(simplificationService.getSimplificationPlan).mockResolvedValueOnce(mockPlan);
    const onSettleMock = vi.fn();

    renderComponent({ groupId: 1, currentUserId: 1, onSettleDebt: onSettleMock });

    expect(await screen.findByText("You")).toBeInTheDocument();
    const settleBtn = screen.getByRole("button", { name: /Settle/i });
    expect(settleBtn).toBeInTheDocument();

    fireEvent.click(settleBtn);

    expect(onSettleMock).toHaveBeenCalledWith({
      from: 1,
      to: 3,
      amount: 75.0,
      toUsername: "Charlie",
    });
  });

  it("displays notice when debt simplification is disabled", async () => {
    const mockPlan: DebtSimplificationPlan = {
      groupId: 1,
      scope: "INTRA_GROUP",
      status: "DISABLED",
      simplificationEnabled: false,
      originalTransactionCount: 0,
      simplifiedTransactionCount: 0,
      totalDebtVolume: 0,
      optedOutUserIds: [],
      transactions: [],
    };

    vi.mocked(simplificationService.getSimplificationPlan).mockResolvedValueOnce(mockPlan);

    renderComponent({ groupId: 1, currentUserId: 1 });

    expect(
      await screen.findByText(/Debt simplification is disabled for this group by governance/i),
    ).toBeInTheDocument();
  });

  it("displays opted out users banner if members opted out", async () => {
    const mockPlan: DebtSimplificationPlan = {
      groupId: 1,
      scope: "INTRA_GROUP",
      status: "COMPLETED",
      simplificationEnabled: true,
      originalTransactionCount: 2,
      simplifiedTransactionCount: 2,
      totalDebtVolume: 40.0,
      optedOutUserIds: [2],
      transactions: [],
    };

    vi.mocked(simplificationService.getSimplificationPlan).mockResolvedValueOnce(mockPlan);

    renderComponent({ groupId: 1, currentUserId: 1 });

    expect(
      await screen.findByText(/1 member\(s\) opted out of debt netting/i),
    ).toBeInTheDocument();
  });
});
