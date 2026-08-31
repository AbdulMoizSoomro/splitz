import { useState, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { groupService } from "./groupService";
import { friendService } from "../users/friendService";
import { useDisplayNames } from "../../hooks/useDisplayName";
import { queryKeys } from "../../lib/queryKeys";
import { toast } from "sonner";
import type { Group } from "../../types/group";

export function useGroupMembership(groupId: number, currentUserId?: number) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [isLeaveModalOpen, setIsLeaveModalOpen] = useState(false);
  const [isSelfDemoteModalOpen, setIsSelfDemoteModalOpen] = useState(false);
  const [isAddMemberModalOpen, setIsAddMemberModalOpen] = useState(false);

  const { data: group, isLoading: isGroupLoading } = useQuery({
    queryKey: queryKeys.group(groupId),
    queryFn: () => groupService.getGroup(groupId),
    enabled: !!groupId,
  });

  const { data: friends, isLoading: isFriendsLoading } = useQuery({
    queryKey: queryKeys.friends(Number(currentUserId)),
    queryFn: () => friendService.getFriends(Number(currentUserId)),
    enabled: !!currentUserId,
  });

  const memberIds = useMemo(
    () => group?.members.map((m) => m.userId) ?? [],
    [group],
  );
  const memberNames = useDisplayNames(memberIds);

  const leaveMutation = useMutation({
    mutationFn: () => groupService.removeMember(groupId, Number(currentUserId)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.groups() });
      toast.success("Left group successfully");
      navigate("/groups");
    },
    onError: () => {
      toast.error("Failed to leave group");
    },
  });

  const updateRoleMutation = useMutation({
    mutationFn: ({
      userId,
      role,
    }: {
      userId: number;
      role: "ADMIN" | "MEMBER";
    }) => groupService.updateMemberRole(groupId, userId, role),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.group(groupId) });
      toast.success("Role updated successfully");
      if (
        variables.userId === Number(currentUserId) &&
        variables.role === "MEMBER"
      ) {
        setIsSelfDemoteModalOpen(false);
      }
    },
    onError: (error) => {
      let message = "Failed to update role";
      if (axios.isAxiosError(error)) {
        message = error.response?.data?.message || message;
      }
      toast.error(message);
      setIsSelfDemoteModalOpen(false);
    },
  });

  const updateGroupMutation = useMutation({
    mutationFn: (data: Partial<Group>) =>
      groupService.updateGroup(groupId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.group(groupId) });
      toast.success("Group settings updated");
    },
    onError: () => {
      toast.error("Failed to update group settings");
    },
  });

  const handleLeave = () => {
    leaveMutation.mutate();
  };

  const handleRoleUpdate = (userId: number, newRole: "ADMIN" | "MEMBER") => {
    if (userId === Number(currentUserId) && newRole === "MEMBER") {
      setIsSelfDemoteModalOpen(true);
      return;
    }
    updateRoleMutation.mutate({ userId, role: newRole });
  };

  const confirmSelfDemote = () => {
    updateRoleMutation.mutate({
      userId: Number(currentUserId),
      role: "MEMBER",
    });
  };

  return {
    group,
    isGroupLoading,
    friends,
    isFriendsLoading,
    memberNames,
    isLeaveModalOpen,
    setIsLeaveModalOpen,
    isSelfDemoteModalOpen,
    setIsSelfDemoteModalOpen,
    isAddMemberModalOpen,
    setIsAddMemberModalOpen,
    handleLeave,
    handleRoleUpdate,
    confirmSelfDemote,
    leaveMutation,
    updateRoleMutation,
    updateGroupMutation,
  };
}
