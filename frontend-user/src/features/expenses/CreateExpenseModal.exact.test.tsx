import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import ExpenseModal from "./ExpenseModal";
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
  members: [
    { userId: 1, role: "ADMIN" },
    { userId: 2, role: "MEMBER" },
    { userId: 3, role: "MEMBER" },
  ],
} as unknown as Group;

const renderModal = () => {
  return render(
    <QueryClientProvider client={queryClient}>
      <ExpenseModal isOpen={true} onClose={vi.fn()} group={mockGroup} />
    </QueryClientProvider>,
  );
};

describe("ExpenseModal - Exact Split", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("allows toggling to Exact split type", () => {
    renderModal();
    const exactToggle = screen.getByLabelText(/exact/i);
    fireEvent.click(exactToggle);
    expect(exactToggle).toBeChecked();
  });

  it("shows input fields for each member when Exact split is selected", () => {
    renderModal();
    fireEvent.click(screen.getByLabelText(/exact/i));

    expect(screen.getByLabelText(/user 1 split value/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/user 2 split value/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/user 3 split value/i)).toBeInTheDocument();
  });

  it("validates that exact shares sum up to total amount", async () => {
    renderModal();
    fireEvent.change(screen.getByLabelText(/description/i), {
      target: { value: "Test" },
    });
    fireEvent.change(screen.getByLabelText(/amount/i), {
      target: { value: "100" },
    });
    fireEvent.click(screen.getByLabelText(/exact/i));

    // Fill in shares that don't sum to 100
    fireEvent.change(screen.getByLabelText(/user 1 split value/i), {
      target: { value: "30" },
    });
    fireEvent.change(screen.getByLabelText(/user 2 split value/i), {
      target: { value: "30" },
    });
    fireEvent.change(screen.getByLabelText(/user 3 split value/i), {
      target: { value: "30" },
    });

    const submitButton = screen.getByRole("button", { name: /add expense/i });
    expect(submitButton).toBeDisabled();

    // Fix shares to sum to 100
    fireEvent.change(screen.getByLabelText(/user 3 split value/i), {
      target: { value: "40" },
    });
    expect(submitButton).not.toBeDisabled();

    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(expenseService.createExpense).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          amount: 100,
          splitType: "EXACT",
          splits: expect.arrayContaining([
            expect.objectContaining({ userId: 1, shareAmount: 30 }),
            expect.objectContaining({ userId: 2, shareAmount: 30 }),
            expect.objectContaining({ userId: 3, shareAmount: 40 }),
          ]),
        }),
      );
    });
  });

  it("shows remaining balance to be allocated", () => {
    renderModal();
    fireEvent.change(screen.getByLabelText(/amount/i), {
      target: { value: "100" },
    });
    fireEvent.click(screen.getByLabelText(/exact/i));

    fireEvent.change(screen.getByLabelText(/user 1 split value/i), {
      target: { value: "30" },
    });
    expect(screen.getByText(/remaining: \$70\.00/i)).toBeInTheDocument();
  });
});
