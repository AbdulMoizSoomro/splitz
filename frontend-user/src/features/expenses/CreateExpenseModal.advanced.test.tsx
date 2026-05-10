import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import CreateExpenseModal from './CreateExpenseModal';
import { expenseService } from './expenseService';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { Group } from '../../types/group';

vi.mock('./expenseService', () => ({
  expenseService: {
    createExpense: vi.fn(),
  },
}));

vi.mock('./categoryService', () => ({
  categoryService: {
    getCategories: vi.fn().mockResolvedValue([]),
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
  name: 'Test Group',
  description: 'A test group',
  createdBy: 1,
  active: true,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  members: [
    { id: 1, userId: 1, role: 'ADMIN', joinedAt: new Date().toISOString() },
    { id: 2, userId: 2, role: 'MEMBER', joinedAt: new Date().toISOString() },
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
    </QueryClientProvider>
  );
};

describe('CreateExpenseModal Advanced Splits', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('submits percentage split correctly', async () => {
    renderModal();
    
    fireEvent.change(screen.getByLabelText(/description/i), { target: { value: 'Dinner' } });
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '100' } });
    
    // Select PERCENTAGE split
    fireEvent.click(screen.getByLabelText(/percentage/i));
    
    // Fill percentages
    const percentageInputs = screen.getAllByLabelText(/split value/i);
    fireEvent.change(percentageInputs[0], { target: { value: '60' } });
    fireEvent.change(percentageInputs[1], { target: { value: '40' } });
    
    expect(screen.getByText(/100% allocated/i)).toBeInTheDocument();

    const submitButton = screen.getByRole('button', { name: /add expense/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(expenseService.createExpense).toHaveBeenCalledWith(1, expect.objectContaining({
        splitType: 'PERCENTAGE',
        splits: [
          { userId: 1, splitType: 'PERCENTAGE', splitValue: 60, shareAmount: undefined },
          { userId: 2, splitType: 'PERCENTAGE', splitValue: 40, shareAmount: undefined },
        ],
      }));
    });
  });

  it('validates percentage split must sum to 100', async () => {
    renderModal();
    
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '100' } });
    fireEvent.click(screen.getByLabelText(/percentage/i));
    
    const percentageInputs = screen.getAllByLabelText(/split value/i);
    fireEvent.change(percentageInputs[0], { target: { value: '50' } });
    fireEvent.change(percentageInputs[1], { target: { value: '40' } });
    
    expect(screen.getByText(/total must equal 100%/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /add expense/i })).toBeDisabled();
  });

  it('submits shares split correctly', async () => {
    renderModal();
    
    fireEvent.change(screen.getByLabelText(/description/i), { target: { value: 'Trip' } });
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '300' } });
    
    fireEvent.click(screen.getByLabelText(/shares/i));
    
    const shareInputs = screen.getAllByLabelText(/split value/i);
    fireEvent.change(shareInputs[0], { target: { value: '2' } });
    fireEvent.change(shareInputs[1], { target: { value: '1' } });
    
    expect(screen.getByText(/total shares: 3/i)).toBeInTheDocument();

    const submitButton = screen.getByRole('button', { name: /add expense/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(expenseService.createExpense).toHaveBeenCalledWith(1, expect.objectContaining({
        splitType: 'SHARES',
        splits: [
          { userId: 1, splitType: 'SHARES', splitValue: 2, shareAmount: undefined },
          { userId: 2, splitType: 'SHARES', splitValue: 1, shareAmount: undefined },
        ],
      }));
    });
  });

  it('submits adjustment split correctly', async () => {
    renderModal();
    
    fireEvent.change(screen.getByLabelText(/description/i), { target: { value: 'Lunch' } });
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '40' } });
    
    fireEvent.click(screen.getByLabelText(/adjustment/i));
    
    const adjInputs = screen.getAllByLabelText(/split value/i);
    fireEvent.change(adjInputs[0], { target: { value: '5' } });
    fireEvent.change(adjInputs[1], { target: { value: '-5' } });
    
    expect(screen.getByText(/adjustments balanced/i)).toBeInTheDocument();

    const submitButton = screen.getByRole('button', { name: /add expense/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(expenseService.createExpense).toHaveBeenCalledWith(1, expect.objectContaining({
        splitType: 'ADJUSTMENT',
        splits: [
          { userId: 1, splitType: 'ADJUSTMENT', splitValue: 5, shareAmount: undefined },
          { userId: 2, splitType: 'ADJUSTMENT', splitValue: -5, shareAmount: undefined },
        ],
      }));
    });
  });

  it('validates adjustments must sum to zero', async () => {
    renderModal();
    
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '40' } });
    fireEvent.click(screen.getByLabelText(/adjustment/i));
    
    const adjInputs = screen.getAllByLabelText(/split value/i);
    fireEvent.change(adjInputs[0], { target: { value: '5' } });
    fireEvent.change(adjInputs[1], { target: { value: '2' } });
    
    expect(screen.getByText(/adjustments must sum to \$0.00/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /add expense/i })).toBeDisabled();
  });
});
