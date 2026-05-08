import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import GroupList from './GroupList';
import { groupService } from './groupService';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

vi.mock('./groupService');

describe('GroupList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();
  });

  it('renders loading state initially', () => {
    vi.mocked(groupService.getGroups).mockReturnValue(new Promise(() => {}));
    render(
      <QueryClientProvider client={queryClient}>
        <GroupList />
      </QueryClientProvider>
    );
    expect(screen.getByTestId('loader')).toBeInTheDocument();
  });

  it('renders empty state when no groups found', async () => {
    vi.mocked(groupService.getGroups).mockResolvedValue([]);
    render(
      <QueryClientProvider client={queryClient}>
        <GroupList />
      </QueryClientProvider>
    );
    await waitFor(() => {
      expect(screen.getByText(/no groups found/i)).toBeInTheDocument();
    });
  });

  it('renders list of groups', async () => {
    const mockGroups = [
      { id: 1, name: 'Group 1', description: 'Desc 1', members: [], createdBy: 1, active: true, createdAt: '', updatedAt: '' },
      { id: 2, name: 'Group 2', description: 'Desc 2', members: [], createdBy: 1, active: true, createdAt: '', updatedAt: '' },
    ];
    vi.mocked(groupService.getGroups).mockResolvedValue(mockGroups);

    render(
      <QueryClientProvider client={queryClient}>
        <GroupList />
      </QueryClientProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Group 1')).toBeInTheDocument();
      expect(screen.getByText('Group 2')).toBeInTheDocument();
      expect(screen.getByText('Desc 1')).toBeInTheDocument();
      expect(screen.getByText('Desc 2')).toBeInTheDocument();
    });
  });
});
