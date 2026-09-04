import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import FriendsList from "./FriendsList";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import * as useLedgerModule from "../../hooks/useLedger";
import * as authStoreModule from "../../store/authStore";
import { friendService } from "./friendService";

vi.mock("./friendService", () => ({
  friendService: {
    getFriendRequests: vi.fn(),
    sendFriendRequest: vi.fn(),
    removeFriend: vi.fn(),
  },
}));

vi.mock("./FriendshipSettlementModal", () => ({
  default: ({ isOpen, onClose, suggestedAmount }: any) =>
    isOpen ? (
      <div data-testid="settlement-modal">
        <span>Modal Amount: {suggestedAmount}</span>
        <button onClick={onClose}>Close Modal</button>
      </div>
    ) : null,
}));

describe("FriendsList", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });

    vi.spyOn(authStoreModule, "useAuthStore").mockImplementation(((selector?: any) => {
      const state = {
        user: {
          id: "1",
          username: "testuser",
          email: "test@example.com",
        },
      };
      return selector ? selector(state) : state;
    }) as any);

    vi.mocked(friendService.getFriendRequests).mockResolvedValue([]);
  });

  it("renders loading spinner while ledger is loading", () => {
    vi.spyOn(useLedgerModule, "useLedger").mockReturnValue({
      key: 1,
      groups: undefined,
      friends: undefined,
      ledger: undefined,
      hasBalances: false,
      groupsLoading: false,
      balancesLoading: false,
      friendsLoading: false,
      isLoading: true,
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <FriendsList />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(screen.queryByText(/you are owed/i)).not.toBeInTheDocument();
  });

  it("renders summary banner, connections, and filters when data loads", async () => {
    vi.spyOn(useLedgerModule, "useLedger").mockReturnValue({
      key: 1,
      groups: undefined,
      friends: [
        {
          id: 101,
          username: "alice",
          firstName: "Alice",
          lastName: "Smith",
          email: "alice@example.com",
        },
        {
          id: 102,
          username: "bob",
          firstName: "Bob",
          lastName: "Jones",
          email: "bob@example.com",
        },
      ],
      ledger: {
        key: 1,
        counterparties: [
          {
            userId: 101,
            name: "Alice Smith",
            username: "alice",
            balance: 50.0,
            groups: [{ id: 1, name: "Ski Trip" }],
          },
          {
            userId: 103,
            name: "Charlie Brown",
            username: "charlie",
            balance: -30.0,
            groups: [{ id: 2, name: "Apartment" }],
          },
        ],
        memberNames: {},
        groupPositions: [],
        position: { total: 20, groupTotal: 20, direct: 0 },
      },
      hasBalances: true,
      groupsLoading: false,
      balancesLoading: false,
      friendsLoading: false,
      isLoading: false,
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <FriendsList />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    // Summary banner values (await async query)
    expect(await screen.findByText(/you are owed/i)).toBeInTheDocument();
    expect(screen.getByText("$50.00")).toBeInTheDocument();
    expect(screen.getAllByText(/you owe/i).length).toBeGreaterThan(0);
    expect(screen.getByText("$30.00")).toBeInTheDocument();
    expect(screen.getByText("+$20.00")).toBeInTheDocument();

    // Connections rendered
    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
    expect(screen.getByText("Charlie Brown")).toBeInTheDocument();
    expect(screen.getByText("Temporary Friends")).toBeInTheDocument();

    // Filter pills
    expect(screen.getByRole("button", { name: /all/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /you owe/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /owed to you/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /settled/i })).toBeInTheDocument();

    // Test filter: click "You Owe"
    fireEvent.click(screen.getByRole("button", { name: /you owe/i }));
    expect(screen.getByText("Charlie Brown")).toBeInTheDocument();
    expect(screen.queryByText("Alice Smith")).not.toBeInTheDocument();
    expect(screen.queryByText("Bob Jones")).not.toBeInTheDocument();

    // Test filter: click "Settled"
    fireEvent.click(screen.getByRole("button", { name: /settled/i }));
    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
    expect(screen.queryByText("Alice Smith")).not.toBeInTheDocument();
    expect(screen.queryByText("Charlie Brown")).not.toBeInTheDocument();

    // Test search
    fireEvent.click(screen.getByRole("button", { name: /all/i }));
    const searchInput = screen.getByPlaceholderText(/search connections or groups/i);
    fireEvent.change(searchInput, { target: { value: "alice" } });
    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.queryByText("Bob Jones")).not.toBeInTheDocument();
    expect(screen.queryByText("Charlie Brown")).not.toBeInTheDocument();

    // Clear search
    fireEvent.change(searchInput, { target: { value: "" } });

    // Click Settle Up for Alice
    const settleButtons = screen.getAllByRole("button", { name: /settle up/i });
    expect(settleButtons.length).toBeGreaterThan(0);
    fireEvent.click(settleButtons[0]);

    // Check modal opened
    expect(screen.getByTestId("settlement-modal")).toBeInTheDocument();
    expect(screen.getByText(/modal amount/i)).toBeInTheDocument();
  });
});
