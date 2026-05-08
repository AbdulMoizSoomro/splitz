import { expenseApi } from '../../lib/axios';
import type { Group, CreateGroupRequest, AddMemberRequest, GroupBalanceResponse, GroupRole } from '../../types/group';
import type { UserBalanceResponse } from '../../types/user';

export const groupService = {
  getGroups: async (): Promise<Group[]> => {
    const response = await expenseApi.get<Group[]>('/groups');
    return response.data;
  },

  getGroup: async (id: number): Promise<Group> => {
    const response = await expenseApi.get<Group>(`/groups/${id}`);
    return response.data;
  },

  createGroup: async (data: CreateGroupRequest): Promise<Group> => {
    const response = await expenseApi.post<Group>('/groups', data);
    return response.data;
  },

  addMember: async (groupId: number, data: AddMemberRequest): Promise<Group> => {
    const response = await expenseApi.post<Group>(`/groups/${groupId}/members`, data);
    return response.data;
  },

  removeMember: async (groupId: number, userId: number): Promise<void> => {
    await expenseApi.delete(`/groups/${groupId}/members/${userId}`);
  },

  updateGroup: async (id: number, data: Partial<Group>): Promise<Group> => {
    const response = await expenseApi.put<Group>(`/groups/${id}`, data);
    return response.data;
  },

  getBalances: async (groupId: number): Promise<GroupBalanceResponse> => {
    const response = await expenseApi.get<GroupBalanceResponse>(`/groups/${groupId}/balances`);
    return response.data;
  },

  updateMemberRole: async (groupId: number, userId: number, role: GroupRole): Promise<Group> => {
    const response = await expenseApi.put<Group>(`/groups/${groupId}/members/${userId}/role`, { role });
    return response.data;
  },

  getUserBalances: async (userId: number): Promise<UserBalanceResponse> => {
    const response = await expenseApi.get<UserBalanceResponse>(`/users/${userId}/balances`);
    return response.data;
  },
};
