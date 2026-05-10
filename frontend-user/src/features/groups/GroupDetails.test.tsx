import {
  render,
  screen,
  waitFor,
  within,
  fireEvent,
} from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import GroupDetails from "./GroupDetails";
import { groupService } from "./groupService";
import { friendService } from "../users/friendService";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { Group } from "../../types/group";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

vi.mock("./groupService");
vi.mock("../users/friendService");
const mockUseAuthStore = vi.fn();
vi.mock("../../store/authStore", () => ({
  useAuthStore: () => mockUseAuthStore(),
}));

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
  createdAt: "2025-01-01T10:00:00Z",
  updatedAt: "2025-01-01T10:00:00Z",
};

describe("GroupDetails", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();
    mockUseAuthStore.mockReturnValue({
      user: { id: "1", username: "testuser" },
    });
    // Default mock for balances (zero balance)
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 1,
      balances: [
        {
          userId: 1,
          username: "testuser",
          email: "t@e.com",
          firstName: "T",
          lastName: "U",
          balance: 0,
        },
      ],
      simplifiedDebts: [],
    });
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
  });

  it("renders group details correctly", async () => {
    vi.mocked(groupService.getGroup).mockResolvedValue(mockGroup);

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText("Test Group")).toBeInTheDocument();
      expect(screen.getByText("Test Description")).toBeInTheDocument();
    });
  });

  it("shows confirmation modal when clicking leave group button", async () => {
    vi.mocked(groupService.getGroup).mockResolvedValue(mockGroup);

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await screen.findByText("Leave Group");
    fireEvent.click(screen.getByText("Leave Group"));

    await waitFor(() => {
      expect(
        screen.getByText(/Are you sure you want to leave this group\?/i),
      ).toBeInTheDocument();
    });
  });

  it("calls removeMember and redirects to groups list when confirmed", async () => {
    vi.mocked(groupService.getGroup).mockResolvedValue(mockGroup);
    vi.mocked(groupService.removeMember).mockResolvedValue();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
            <Route path="/groups" element={<div>Groups List</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await screen.findByText("Leave Group");
    fireEvent.click(screen.getByText("Leave Group"));

    // Confirm in modal
    const modal = screen.getByRole("dialog");
    await waitFor(() => {
      const confirmButton = within(modal).getByRole("button", {
        name: /^leave group$/i,
      });
      expect(confirmButton).not.toBeDisabled();
    });

    const confirmButton = within(modal).getByRole("button", {
      name: /^leave group$/i,
    });
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(groupService.removeMember).toHaveBeenCalledWith(1, 1);
      expect(screen.getByText("Groups List")).toBeInTheDocument();
    });
  });

  it("blocks leaving group if user has outstanding balance", async () => {
    vi.mocked(groupService.getGroup).mockResolvedValue(mockGroup);
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 1,
      balances: [
        {
          userId: 1,
          username: "testuser",
          email: "t@e.com",
          firstName: "T",
          lastName: "U",
          balance: 15.5,
        },
      ],
      simplifiedDebts: [],
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await screen.findByText("Leave Group");
    fireEvent.click(screen.getByText("Leave Group"));

    await waitFor(() => {
      const modal = screen.getByRole("dialog");
      expect(
        within(modal).getByText(
          /You cannot leave this group while you have an outstanding balance \(15.5\)/i,
        ),
      ).toBeInTheDocument();
      expect(
        within(modal).getByRole("button", { name: /^leave group$/i }),
      ).toBeDisabled();
    });
  });

  it("renders member list with roles and names", async () => {
    const groupWithMembers: Group = {
      ...mockGroup,
      createdBy: 1,
      members: [
        { id: 1, userId: 1, role: "ADMIN", joinedAt: "2025-01-01T10:00:00Z" },
        { id: 2, userId: 2, role: "ADMIN", joinedAt: "2025-01-01T10:00:00Z" },
        { id: 3, userId: 3, role: "MEMBER", joinedAt: "2025-01-01T10:00:00Z" },
      ],
    };
    vi.mocked(groupService.getGroup).mockResolvedValue(groupWithMembers);
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 1,
      balances: [
        {
          userId: 1,
          username: "owneruser",
          email: "o@e.com",
          firstName: "Owner",
          lastName: "User",
          balance: 0,
        },
        {
          userId: 2,
          username: "adminuser",
          email: "a@e.com",
          firstName: "Admin",
          lastName: "User",
          balance: 0,
        },
        {
          userId: 3,
          username: "memberuser",
          email: "m@e.com",
          firstName: "Member",
          lastName: "User",
          balance: 0,
        },
      ],
      simplifiedDebts: [],
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await screen.findByText("Test Group");
    const membersTab = screen
      .getAllByRole("button")
      .find((b) => b.textContent?.includes("Members"));
    if (membersTab) fireEvent.click(membersTab);

    await waitFor(() => {
      expect(screen.getByText("Owner User")).toBeInTheDocument();
      expect(screen.getByText("Admin User")).toBeInTheDocument();
      expect(screen.getByText("Member User")).toBeInTheDocument();
    });
  });

  it("renders Temp Friend badge for non-friends", async () => {
    const groupWithMembers: Group = {
      ...mockGroup,
      createdBy: 1,
      members: [
        { id: 1, userId: 1, role: "ADMIN", joinedAt: "2025-01-01T10:00:00Z" },
        { id: 2, userId: 2, role: "MEMBER", joinedAt: "2025-01-01T10:00:00Z" },
        { id: 3, userId: 3, role: "MEMBER", joinedAt: "2025-01-01T10:00:00Z" },
      ],
    };
    vi.mocked(groupService.getGroup).mockResolvedValue(groupWithMembers);
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 1,
      balances: [
        {
          userId: 1,
          username: "owneruser",
          email: "o@e.com",
          firstName: "Owner",
          lastName: "User",
          balance: 0,
        },
        {
          userId: 2,
          username: "adminuser",
          email: "a@e.com",
          firstName: "Admin",
          lastName: "User",
          balance: 0,
        },
        {
          userId: 3,
          username: "memberuser",
          email: "m@e.com",
          firstName: "Member",
          lastName: "User",
          balance: 0,
        },
      ],
      simplifiedDebts: [],
    });

    vi.mocked(friendService.getFriends).mockResolvedValue([
      {
        id: 2,
        username: "adminuser",
        email: "a@e.com",
        firstName: "Admin",
        lastName: "User",
      },
    ]);

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await screen.findByText("Test Group");
    const membersTab = screen
      .getAllByRole("button")
      .find((b) => b.textContent?.includes("Members"));
    if (membersTab) fireEvent.click(membersTab);

    await waitFor(() => {
      expect(screen.getByText("Member User")).toBeInTheDocument();
      expect(screen.getByText("Temp Friend")).toBeInTheDocument();
    });
  });

  it("calls updateMemberRole when promoting a member", async () => {
    const groupWithMembers: Group = {
      ...mockGroup,
      createdBy: 1,
      members: [
        { id: 1, userId: 1, role: "ADMIN", joinedAt: "2025-01-01T10:00:00Z" },
        { id: 3, userId: 3, role: "MEMBER", joinedAt: "2025-01-01T10:00:00Z" },
      ],
    };
    vi.mocked(groupService.getGroup).mockResolvedValue(groupWithMembers);
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 1,
      balances: [
        {
          userId: 1,
          username: "owneruser",
          email: "o@e.com",
          firstName: "Owner",
          lastName: "User",
          balance: 0,
        },
        {
          userId: 3,
          username: "memberuser",
          email: "m@e.com",
          firstName: "Member",
          lastName: "User",
          balance: 0,
        },
      ],
      simplifiedDebts: [],
    });
    vi.mocked(groupService.updateMemberRole).mockResolvedValue({
      ...groupWithMembers,
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await screen.findByText("Test Group");
    const membersTab = screen
      .getAllByRole("button")
      .find((b) => b.textContent?.includes("Members"));
    if (membersTab) fireEvent.click(membersTab);

    await screen.findByText("Member User");
    const dropdownTrigger = screen.getByLabelText("Manage role");
    fireEvent.click(dropdownTrigger);

    const promoteButton = await screen.findByText("Promote to Admin");
    fireEvent.click(promoteButton);

    await waitFor(() => {
      expect(groupService.updateMemberRole).toHaveBeenCalledWith(1, 3, "ADMIN");
    });
  });

  it("shows Group Settings only to the Owner", async () => {
    vi.mocked(groupService.getGroup).mockResolvedValue(mockGroup);

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText("Group Settings")).toBeInTheDocument();
    });
  });

  it("hides Group Settings from non-Owner members", async () => {
    mockUseAuthStore.mockReturnValue({
      user: { id: "2", username: "otheruser" },
    });

    const groupWithOwner1 = { ...mockGroup, createdBy: 1 };
    vi.mocked(groupService.getGroup).mockResolvedValue(groupWithOwner1);

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.queryByText("Group Settings")).not.toBeInTheDocument();
    });
  });
});
