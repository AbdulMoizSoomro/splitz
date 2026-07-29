import {
  render,
  screen,
  waitFor,
  within,
  fireEvent,
} from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup } from "@testing-library/react";
import GroupDetails from "./GroupDetails";
import { groupService } from "./groupService";
import { friendService } from "../users/friendService";
import { settlementService } from "../balances/settlementService";
import { expenseService } from "../expenses/expenseService";
import { categoryService } from "../expenses/categoryService";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { Group } from "../../types/group";

let queryClient: QueryClient;

vi.mock("./groupService");
vi.mock("../users/friendService");
vi.mock("../balances/settlementService");
vi.mock("../expenses/expenseService");
vi.mock("../expenses/categoryService");
const mockUseAuthStore = vi.fn();
vi.mock("../../store/authStore", () => ({
  useAuthStore: (selector?: (s: { user: { id: string; username: string; email: string } | null }) => unknown) => {
    const state = mockUseAuthStore();
    return selector ? selector(state) : state;
  },
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
  allowMembersToEditExpenses: true,
  createdAt: "2025-01-01T10:00:00Z",
  updatedAt: "2025-01-01T10:00:00Z",
};

describe("GroupDetails", () => {
  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          gcTime: 0,
          staleTime: 0,
        },
      },
    });
    queryClient.clear();
    vi.clearAllMocks();
    mockUseAuthStore.mockReturnValue({
      user: { id: "1", username: "testuser" },
    });
    vi.mocked(groupService.getGroup).mockResolvedValue(mockGroup);
    vi.mocked(groupService.getGroups).mockResolvedValue([mockGroup]);
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 1,
      balances: [],
      simplifiedDebts: [],
    });
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(settlementService.getSettlementsByGroup).mockResolvedValue([]);
    vi.mocked(expenseService.getGroupExpenses).mockResolvedValue([]);
    vi.mocked(categoryService.getCategories).mockResolvedValue([]);
    vi.mocked(groupService.getGroupActivity).mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
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
      expect(screen.getAllByText("Test Group")[0]).toBeInTheDocument();
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

    const membersTab = await screen.findByRole("button", { name: /members/i });
    fireEvent.click(membersTab);

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

    const membersTab = await screen.findByRole("button", { name: /members/i });
    fireEvent.click(membersTab);

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

    const membersTab = await screen.findByRole("button", { name: /members/i });
    fireEvent.click(membersTab);

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
    vi.mocked(groupService.getGroups).mockResolvedValue([groupWithMembers]);
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

    const testQueryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={testQueryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getAllByText("Test Group")[0]).toBeInTheDocument());
    const membersTab = await screen.findByText("Members");
    fireEvent.click(membersTab);

    await waitFor(() => {
      // Current user (userId=1, Owner) is shown as "You" by the hook
      expect(screen.getByText("You")).toBeInTheDocument();
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
    vi.mocked(groupService.getGroups).mockResolvedValue([groupWithMembers]);
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

    const testQueryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={testQueryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getAllByText("Test Group")[0]).toBeInTheDocument());
    const membersTab = await screen.findByText("Members");
    fireEvent.click(membersTab);

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
    vi.mocked(groupService.getGroups).mockResolvedValue([groupWithMembers]);
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

    const testQueryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={testQueryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getAllByText("Test Group")[0]).toBeInTheDocument());
    const membersTab = await screen.findByText("Members");
    fireEvent.click(membersTab);

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

    const testQueryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={testQueryClient}>
        <MemoryRouter initialEntries={["/groups/1"]}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const membersTab = await screen.findByRole("button", { name: /members/i });
    fireEvent.click(membersTab);

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

    const testQueryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={testQueryClient}>
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
