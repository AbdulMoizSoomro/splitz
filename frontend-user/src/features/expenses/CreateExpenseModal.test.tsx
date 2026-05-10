import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import CreateExpenseModal from "./CreateExpenseModal";
import { expenseService } from "./expenseService";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { Group } from "../../types/group";

vi.mock("./expenseService", () => ({
  expenseService: {
    createExpense: vi.fn(),
  },
}));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

const mockGroup = {
  id: 1,
  name: "Test Group",
  description: "A test group",
  createdBy: 1,
  active: true,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  members: [
    { id: 1, userId: 1, role: "ADMIN", joinedAt: new Date().toISOString() },
    { id: 2, userId: 2, role: "MEMBER", joinedAt: new Date().toISOString() },
    { id: 3, userId: 3, role: "MEMBER", joinedAt: new Date().toISOString() },
  ],
} as unknown as Group;

const renderModal = (props = {}) => {
  return render(
    <QueryClientProvider client={queryClient}>
      <CreateExpenseModal
        isOpen={true}
        onClose={vi.fn()}
        group={mockGroup}
        {...props}
      />
    </QueryClientProvider>,
  );
};

describe("CreateExpenseModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders correctly when open", () => {
    renderModal();
    expect(screen.getByText(/add new expense/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/amount/i)).toBeInTheDocument();
  });

  it("calculates equal split automatically", async () => {
    renderModal();

    fireEvent.change(screen.getByLabelText(/description/i), {
      target: { value: "Lunch" },
    });
    fireEvent.change(screen.getByLabelText(/amount/i), {
      target: { value: "30" },
    });

    const submitButton = screen.getByRole("button", { name: /add expense/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(expenseService.createExpense).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          description: "Lunch",
          amount: 30,
          splitType: "EQUAL",
          splits: expect.arrayContaining([
            expect.objectContaining({ userId: 1, splitType: "EQUAL" }),
            expect.objectContaining({ userId: 2, splitType: "EQUAL" }),
            expect.objectContaining({ userId: 3, splitType: "EQUAL" }),
          ]),
        }),
      );
    });
  });

  it("validates that at least one member is selected", async () => {
    renderModal();

    fireEvent.change(screen.getByLabelText(/amount/i), {
      target: { value: "30" },
    });

    // Unselect all members
    const checkboxes = screen.getAllByRole("checkbox");
    checkboxes.forEach((checkbox) => {
      if ((checkbox as HTMLInputElement).checked) {
        fireEvent.click(checkbox);
      }
    });

    const submitButton = screen.getByRole("button", { name: /add expense/i });
    expect(submitButton).toBeDisabled();
    expect(screen.getByText(/select at least one member/i)).toBeInTheDocument();
  });
});
