import { describe, it, expect, vi, beforeEach } from 'vitest';
import { groupService } from './groupService';
import { expenseApi } from '../../lib/axios';

vi.mock('../../lib/axios', () => ({
  expenseApi: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  userApi: {
    get: vi.fn(),
    post: vi.fn(),
  },
  default: {
    get: vi.fn(),
    post: vi.fn(),
  }
}));

describe('groupService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches all groups for the current user', async () => {
    const mockGroups = [
      { id: 1, name: 'Group 1', description: 'Desc 1', members: [] },
      { id: 2, name: 'Group 2', description: 'Desc 2', members: [] },
    ];
    vi.mocked(expenseApi.get).mockResolvedValueOnce({ data: mockGroups });

    const result = await groupService.getGroups();

    expect(expenseApi.get).toHaveBeenCalledWith('/groups');
    expect(result).toEqual(mockGroups);
  });

  it('creates a new group', async () => {
    const newGroup = { name: 'New Group', description: 'New Desc' };
    const mockResponse = { id: 3, ...newGroup, members: [] };
    vi.mocked(expenseApi.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await groupService.createGroup(newGroup);

    expect(expenseApi.post).toHaveBeenCalledWith('/groups', newGroup);
    expect(result).toEqual(mockResponse);
  });

  it('adds a member to a group', async () => {
    const groupId = 1;
    const addMemberRequest = { userId: 2, role: 'MEMBER' as const };
    const mockResponse = { id: groupId, name: 'Group 1', members: [{ userId: 2, role: 'MEMBER' }] };
    vi.mocked(expenseApi.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await groupService.addMember(groupId, addMemberRequest);

    expect(expenseApi.post).toHaveBeenCalledWith(`/groups/${groupId}/members`, addMemberRequest);
    expect(result).toEqual(mockResponse);
  });

  it('removes a member from a group', async () => {
    const groupId = 1;
    const userId = 2;
    vi.mocked(expenseApi.delete).mockResolvedValueOnce({});

    await groupService.removeMember(groupId, userId);

    expect(expenseApi.delete).toHaveBeenCalledWith(`/groups/${groupId}/members/${userId}`);
  });

  it('fetches balances for a group', async () => {
    const groupId = 1;
    const mockBalances = {
      groupId,
      balances: [
        { userId: 1, username: 'user1', email: 'u1@ex.com', firstName: 'U1', lastName: 'L1', balance: 10.0 }
      ],
      simplifiedDebts: []
    };
    vi.mocked(expenseApi.get).mockResolvedValueOnce({ data: mockBalances });

    const result = await groupService.getBalances(groupId);

    expect(expenseApi.get).toHaveBeenCalledWith(`/groups/${groupId}/balances`);
    expect(result).toEqual(mockBalances);
  });
});
