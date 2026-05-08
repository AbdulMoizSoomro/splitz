import { render, screen, waitFor, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import GroupDetails from './GroupDetails';
import { groupService } from './groupService';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { Group } from '../../types/group';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

vi.mock('./groupService');
vi.mock('../../store/authStore', () => ({
  useAuthStore: () => ({
    user: { id: '1', username: 'testuser' }
  })
}));

const mockGroup: Group = {
  id: 1,
  name: 'Test Group',
  description: 'Test Description',
  members: [{ id: 1, userId: 1, role: 'ADMIN', joinedAt: '2025-01-01T10:00:00Z' }],
  createdBy: 1,
  active: true,
  createdAt: '2025-01-01T10:00:00Z',
  updatedAt: '2025-01-01T10:00:00Z'
};

describe('GroupDetails', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();
    // Default mock for balances (zero balance)
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 1,
      balances: [
        { userId: 1, username: 'testuser', email: 't@e.com', firstName: 'T', lastName: 'U', balance: 0 }
      ],
      simplifiedDebts: []
    });
  });

  it('renders group details correctly', async () => {
    vi.mocked(groupService.getGroup).mockResolvedValue(mockGroup);

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/groups/1']}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Test Group')).toBeInTheDocument();
      expect(screen.getByText('Test Description')).toBeInTheDocument();
    });
  });

  it('shows confirmation modal when clicking leave group button', async () => {
    vi.mocked(groupService.getGroup).mockResolvedValue(mockGroup);

    const { fireEvent } = await import('@testing-library/react');

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/groups/1']}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    await screen.findByText('Leave Group');
    fireEvent.click(screen.getByText('Leave Group'));

    await waitFor(() => {
      expect(screen.getByText(/Are you sure you want to leave this group\?/i)).toBeInTheDocument();
    });
  });

  it('calls removeMember and redirects to groups list when confirmed', async () => {
    vi.mocked(groupService.getGroup).mockResolvedValue(mockGroup);
    vi.mocked(groupService.removeMember).mockResolvedValue();

    const { fireEvent } = await import('@testing-library/react');

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/groups/1']}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
            <Route path="/groups" element={<div>Groups List</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    await screen.findByText('Leave Group');
    fireEvent.click(screen.getByText('Leave Group'));

    // Confirm in modal
    const modal = screen.getByRole('dialog');
    await waitFor(() => {
      const confirmButton = within(modal).getByRole('button', { name: /^leave group$/i });
      expect(confirmButton).not.toBeDisabled();
    });

    const confirmButton = within(modal).getByRole('button', { name: /^leave group$/i });
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(groupService.removeMember).toHaveBeenCalledWith(1, 1);
      expect(screen.getByText('Groups List')).toBeInTheDocument();
    });
  });

  it('blocks leaving group if user has outstanding balance', async () => {
    vi.mocked(groupService.getGroup).mockResolvedValue(mockGroup);
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 1,
      balances: [
        { userId: 1, username: 'testuser', email: 't@e.com', firstName: 'T', lastName: 'U', balance: 15.50 }
      ],
      simplifiedDebts: []
    });

    const { fireEvent } = await import('@testing-library/react');

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/groups/1']}>
          <Routes>
            <Route path="/groups/:id" element={<GroupDetails />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    await screen.findByText('Leave Group');
    fireEvent.click(screen.getByText('Leave Group'));

    await waitFor(() => {
      const modal = screen.getByRole('dialog');
      expect(within(modal).getByText(/You cannot leave this group while you have an outstanding balance \(15.5\)/i)).toBeInTheDocument();
      expect(within(modal).getByRole('button', { name: /^leave group$/i })).toBeDisabled();
    });
  });
});
