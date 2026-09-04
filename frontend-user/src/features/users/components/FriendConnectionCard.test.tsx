import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { FriendConnectionCard } from "./FriendConnectionCard";
import type { UnifiedConnection } from "../friendsLedger";

describe("FriendConnectionCard", () => {
  const confirmedFriendOwes: UnifiedConnection = {
    userId: 101,
    name: "Alice Smith",
    username: "alicesmith",
    firstName: "Alice",
    lastName: "Smith",
    isFriend: true,
    isPendingOutgoing: false,
    balance: 45.5,
    groups: [
      { id: 1, name: "Ski Trip" },
      { id: 2, name: "Tahoe" },
      { id: 3, name: "Apartment" },
    ],
  };

  const groupConnectionOwed: UnifiedConnection = {
    userId: 102,
    name: "Bob Jones",
    username: "bobjones",
    firstName: "Bob",
    lastName: "Jones",
    isFriend: false,
    isPendingOutgoing: false,
    balance: -20.0,
    groups: [{ id: 1, name: "Ski Trip" }],
  };

  const settledFriend: UnifiedConnection = {
    userId: 103,
    name: "Charlie Settled",
    username: "charlie",
    firstName: "Charlie",
    lastName: "Settled",
    isFriend: true,
    isPendingOutgoing: false,
    balance: 0,
    groups: [],
  };

  it("renders friend who owes money with Settle Up and groups with +N more", () => {
    const onSettleUp = vi.fn();

    render(
      <MemoryRouter>
        <FriendConnectionCard
          connection={confirmedFriendOwes}
          onSettleUp={onSettleUp}
          onAddFriend={vi.fn()}
          onRemoveFriend={vi.fn()}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.getByText("@alicesmith")).toBeInTheDocument();
    expect(screen.getByText(/owes you \$45\.50/i)).toBeInTheDocument();

    // Top 2 groups
    expect(screen.getByText("Ski Trip")).toBeInTheDocument();
    expect(screen.getByText("Tahoe")).toBeInTheDocument();
    // +1 more badge
    expect(screen.getByText("+1 more")).toBeInTheDocument();

    // Settle Up button
    const settleBtn = screen.getByRole("button", { name: /settle up/i });
    expect(settleBtn).toBeInTheDocument();
    fireEvent.click(settleBtn);
    expect(onSettleUp).toHaveBeenCalledWith(confirmedFriendOwes);
  });

  it("renders temporary group connection with badge and Add Friend CTA", () => {
    const onAddFriend = vi.fn();

    render(
      <MemoryRouter>
        <FriendConnectionCard
          connection={groupConnectionOwed}
          onSettleUp={vi.fn()}
          onAddFriend={onAddFriend}
          onRemoveFriend={vi.fn()}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
    expect(screen.getByText(/temporary friends/i)).toBeInTheDocument();
    expect(screen.getByText(/you owe \$20\.00/i)).toBeInTheDocument();

    const addFriendBtn = screen.getByRole("button", { name: /add friend/i });
    expect(addFriendBtn).toBeInTheDocument();
    fireEvent.click(addFriendBtn);
    expect(onAddFriend).toHaveBeenCalledWith(groupConnectionOwed);
  });

  it("renders settled friend with Settled up status and no Settle Up button", () => {
    render(
      <MemoryRouter>
        <FriendConnectionCard
          connection={settledFriend}
          onSettleUp={vi.fn()}
          onAddFriend={vi.fn()}
          onRemoveFriend={vi.fn()}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText("Charlie Settled")).toBeInTheDocument();
    expect(screen.getByText(/settled up/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /settle up/i })).not.toBeInTheDocument();
  });
});
