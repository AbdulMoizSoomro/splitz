import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import TempFriendsList from './TempFriendsList';
import { friendService } from './friendService';
import { groupService } from '../groups/groupService';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

vi.mock('./friendService');
vi.mock('../groups/groupService');
vi.mock('../../store/authStore', () => ({
  useAuthStore: vi.fn((selector) => selector({
    user: { id: '1', username: 'testuser' }
  }))
}));

describe('TempFriendsList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();
  });

  it('renders nothing when there are no temp friends', async () => {
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(groupService.getUserBalances).mockResolvedValue({
      userId: 1,
      username: 'testuser',
      email: 'test@example.com',
      totalBalance: 0,
      groupBalances: []
    });

    render(
      <QueryClientProvider client={queryClient}>
        <TempFriendsList />
      </QueryClientProvider>
    );

    // Should render empty or nothing
    expect(screen.queryByText('Temporary Friends')).not.toBeInTheDocument();
  });

  it('renders temp friends with non-zero balances', async () => {
    // Current user is 1
    // Friend is 2
    // Non-friend is 3
    vi.mocked(friendService.getFriends).mockResolvedValue([
      { id: 2, username: 'friend', firstName: 'Friend', lastName: 'User', email: 'f@e.com' }
    ]);

    // User is in Group 10
    vi.mocked(groupService.getUserBalances).mockResolvedValue({
      userId: 1,
      username: 'testuser',
      email: 'test@example.com',
      totalBalance: 10,
      groupBalances: [
        { groupId: 10, groupName: 'Group A', balance: 10 }
      ]
    });

    // Group 10 has debts: User 3 owes User 1 $10
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 10,
      balances: [],
      simplifiedDebts: [
        { from: 3, fromUsername: 'tempfriend', to: 1, toUsername: 'testuser', amount: 10 }
      ]
    });

    // We also need user 3's details if we want to show their name
    // The simplifiedDebts should ideally have enough info, or we fetch it.
    // Let's assume the component shows the username from DebtDTO.

    render(
      <QueryClientProvider client={queryClient}>
        <TempFriendsList />
      </QueryClientProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Temporary Friends')).toBeInTheDocument();
      expect(screen.getByText('tempfriend')).toBeInTheDocument();
      expect(screen.getByText('Group A')).toBeInTheDocument(); // Group name check
      expect(screen.getByText(/owes you 10.00/i)).toBeInTheDocument();
      expect(screen.getByText('Add Friend')).toBeInTheDocument();
    });
  });

  it('shows "Cancel Request" if a friend request was already sent', async () => {
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(groupService.getUserBalances).mockResolvedValue({
      userId: 1,
      username: 'testuser',
      email: 'test@example.com',
      totalBalance: 10,
      groupBalances: [{ groupId: 10, groupName: 'Group A', balance: 10 }]
    });
    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 10,
      balances: [],
      simplifiedDebts: [{ from: 3, fromUsername: 'tempfriend', to: 1, toUsername: 'testuser', amount: 10 }]
    });

    // Mock outgoing request to user 3
    vi.mocked(friendService.getFriendRequests).mockResolvedValue([
      { id: 100, requesterId: 1, addresseeId: 3, status: 'PENDING', createdAt: '', updatedAt: '' }
    ]);

    render(
      <QueryClientProvider client={queryClient}>
        <TempFriendsList />
      </QueryClientProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('tempfriend')).toBeInTheDocument();
      expect(screen.getByText('Cancel Request')).toBeInTheDocument();
    });
  });

  it('auto-dismisses temp friends when balance hits zero', async () => {
    vi.mocked(friendService.getFriends).mockResolvedValue([]);
    vi.mocked(groupService.getUserBalances).mockResolvedValue({
      userId: 1,
      username: 'testuser',
      email: 'test@example.com',
      totalBalance: 0,
      groupBalances: [
        { groupId: 10, groupName: 'Group A', balance: 0 }
      ]
    });

    vi.mocked(groupService.getBalances).mockResolvedValue({
      groupId: 10,
      balances: [],
      simplifiedDebts: [] // No debts involving user 1
    });

    render(
      <QueryClientProvider client={queryClient}>
        <TempFriendsList />
      </QueryClientProvider>
    );

    await waitFor(() => {
      expect(screen.queryByText('Temporary Friends')).not.toBeInTheDocument();
    });
  });
});
