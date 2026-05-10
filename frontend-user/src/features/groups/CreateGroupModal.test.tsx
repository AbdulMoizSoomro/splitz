import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import CreateGroupModal from "./CreateGroupModal";
import { groupService } from "./groupService";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import api from "../../lib/axios";

import { useAuthStore } from "../../store/authStore";
import type { Group } from "../../types/group";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

vi.mock("./groupService");
vi.mock("../../lib/axios", () => ({
  default: {
    get: vi.fn(),
  },
  expenseApi: {
    post: vi.fn(),
  },
  userApi: {
    get: vi.fn(),
  },
}));

describe("CreateGroupModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();
    useAuthStore.setState({
      user: { id: "1", username: "testuser", email: "test@example.com" },
      token: "fake-token",
    });
  });

  it("renders correctly when open", () => {
    render(
      <QueryClientProvider client={queryClient}>
        <CreateGroupModal isOpen={true} onClose={() => {}} />
      </QueryClientProvider>,
    );
    expect(screen.getByText(/create new group/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/group name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
  });

  it("validates required fields", async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <CreateGroupModal isOpen={true} onClose={() => {}} />
      </QueryClientProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: /create group/i }));

    expect(
      await screen.findByText(/group name is required/i),
    ).toBeInTheDocument();
  });

  it("calls createGroup and addMember on submit", async () => {
    const mockFriends = [
      { id: 101, firstName: "Friend", lastName: "One", username: "friend1" },
      { id: 102, firstName: "Friend", lastName: "Two", username: "friend2" },
    ];
    vi.mocked(api.get).mockResolvedValue({ data: mockFriends });
    vi.mocked(groupService.createGroup).mockResolvedValue({
      id: 1,
      name: "Test Group",
    } as unknown as Group);
    vi.mocked(groupService.addMember).mockResolvedValue({} as unknown as Group);

    render(
      <QueryClientProvider client={queryClient}>
        <CreateGroupModal isOpen={true} onClose={() => {}} />
      </QueryClientProvider>,
    );

    fireEvent.change(screen.getByLabelText(/group name/i), {
      target: { value: "Test Group" },
    });

    // Wait for friends to load
    await waitFor(() => {
      expect(screen.getByText("Friend One")).toBeInTheDocument();
    });

    // Select a friend
    fireEvent.click(screen.getByText("Friend One"));

    fireEvent.click(screen.getByRole("button", { name: /create group/i }));

    await waitFor(() => {
      expect(groupService.createGroup).toHaveBeenCalledWith({
        name: "Test Group",
        description: "",
        memberUserIds: [101],
      });
      expect(groupService.addMember).not.toHaveBeenCalled();
    });
  });

  it("displays error message when creation fails", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] });
    vi.mocked(groupService.createGroup).mockRejectedValue({
      response: { data: { message: "Failed to create group" } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <CreateGroupModal isOpen={true} onClose={() => {}} />
      </QueryClientProvider>,
    );

    fireEvent.change(screen.getByLabelText(/group name/i), {
      target: { value: "Test Group" },
    });
    fireEvent.click(screen.getByRole("button", { name: /create group/i }));

    expect(
      await screen.findByText("Failed to create group"),
    ).toBeInTheDocument();
  });
});
