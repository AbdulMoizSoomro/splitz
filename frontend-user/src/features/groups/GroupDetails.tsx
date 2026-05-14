import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import axios from "axios";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { groupService } from "./groupService";
import { friendService } from "../users/friendService";
import { useAuthStore } from "../../store/authStore";
import DashboardLayout from "../../components/layout/DashboardLayout";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "../../components/core/Card/Card";
import Button from "../../components/core/Button/Button";
import Modal from "../../components/core/Modal/Modal";
import Badge from "../../components/core/Badge/Badge";
import type { BadgeVariant } from "../../components/core/Badge/Badge";
import Dropdown from "../../components/core/Dropdown/Dropdown";
import { useToastStore } from "../../store/toastStore";
import AddMemberModal from "./AddMemberModal";
import CreateExpenseModal from "../expenses/CreateExpenseModal";
import GroupBalances from "../balances/GroupBalances";
import GroupActivity from "./GroupActivity";
import {
  Loader2,
  ArrowLeft,
  LogOut,
  Users,
  MoreVertical,
  ShieldAlert,
  Settings,
  UserPlus,
  Activity,
  DollarSign,
  Plus,
} from "lucide-react";

const GroupDetails = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuthStore();
  const { addToast } = useToastStore();
  const [isLeaveModalOpen, setIsLeaveModalOpen] = useState(false);
  const [isSelfDemoteModalOpen, setIsSelfDemoteModalOpen] = useState(false);
  const [isAddMemberModalOpen, setIsAddMemberModalOpen] = useState(false);
  const [isAddExpenseModalOpen, setIsAddExpenseModalOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<
    "activity" | "members" | "balances"
  >("activity");

  const { data: group, isLoading } = useQuery({
    queryKey: ["group", id],
    queryFn: () => groupService.getGroup(Number(id)),
    enabled: !!id,
  });

  const { data: balancesResponse, isLoading: isBalancesLoading } = useQuery({
    queryKey: ["group-balances", id],
    queryFn: () => groupService.getBalances(Number(id)),
    enabled: !!id,
  });

  const { data: friends, isLoading: isFriendsLoading } = useQuery({
    queryKey: ["friends", user?.id],
    queryFn: () => friendService.getFriends(Number(user?.id)),
    enabled: !!user?.id,
  });

  const leaveMutation = useMutation({
    mutationFn: () => groupService.removeMember(Number(id), Number(user?.id)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      addToast("Left group successfully", "success");
      navigate("/groups");
    },
    onError: () => {
      addToast("Failed to leave group", "error");
    },
  });

  const updateRoleMutation = useMutation({
    mutationFn: ({
      userId,
      role,
    }: {
      userId: number;
      role: "ADMIN" | "MEMBER";
    }) => groupService.updateMemberRole(Number(id), userId, role),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["group", id] });
      addToast("Role updated successfully", "success");
      if (
        variables.userId === Number(user?.id) &&
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
      addToast(message, "error");
      setIsSelfDemoteModalOpen(false);
    },
  });

  const updateGroupMutation = useMutation({
    mutationFn: (data: Partial<import("../../types/group").Group>) =>
      groupService.updateGroup(Number(id), data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group", id] });
      addToast("Group settings updated", "success");
    },
    onError: () => {
      addToast("Failed to update group settings", "error");
    },
  });

  const handleLeave = () => {
    leaveMutation.mutate();
  };

  const handleRoleUpdate = (userId: number, newRole: "ADMIN" | "MEMBER") => {
    if (userId === Number(user?.id) && newRole === "MEMBER") {
      setIsSelfDemoteModalOpen(true);
      return;
    }
    updateRoleMutation.mutate({ userId, role: newRole });
  };

  const confirmSelfDemote = () => {
    updateRoleMutation.mutate({ userId: Number(user?.id), role: "MEMBER" });
  };

  const currentUserBalance =
    balancesResponse?.balances.find((b) => b.userId === Number(user?.id))
      ?.balance ?? 0;
  const currentUserRole = group?.members.find(
    (m) => m.userId === Number(user?.id),
  )?.role;
  const isOwner = group?.createdBy === Number(user?.id);
  const isAdmin = currentUserRole === "ADMIN";

  const canLeave = currentUserBalance === 0;

  if (isLoading || isFriendsLoading) {
    return (
      <DashboardLayout>
        <div className="flex justify-center py-12">
          <Loader2 className="animate-spin text-blue-600" size={48} />
        </div>
      </DashboardLayout>
    );
  }

  if (!group) {
    return (
      <DashboardLayout>
        <div className="text-center py-12">
          <h2 className="text-xl font-bold text-gray-900">Group not found</h2>
          <Button onClick={() => navigate("/groups")} className="mt-4">
            Back to Groups
          </Button>
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="sm" onClick={() => navigate("/groups")}>
            <ArrowLeft size={20} />
          </Button>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{group.name}</h1>
            {group.description && (
              <p className="text-gray-500">{group.description}</p>
            )}
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="border-b border-gray-200">
          <nav className="-mb-px flex space-x-8">
            <button
              onClick={() => setActiveTab("activity")}
              className={`py-4 px-1 border-b-2 font-medium text-sm transition-colors ${
                activeTab === "activity"
                  ? "border-blue-500 text-blue-600"
                  : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
              }`}
            >
              <div className="flex items-center gap-2">
                <Activity size={18} />
                <span>Activity</span>
              </div>
            </button>
            <button
              onClick={() => setActiveTab("members")}
              className={`py-4 px-1 border-b-2 font-medium text-sm transition-colors ${
                activeTab === "members"
                  ? "border-blue-500 text-blue-600"
                  : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
              }`}
            >
              <div className="flex items-center gap-2">
                <Users size={18} />
                <span>Members</span>
              </div>
            </button>
            <button
              onClick={() => setActiveTab("balances")}
              className={`py-4 px-1 border-b-2 font-medium text-sm transition-colors ${
                activeTab === "balances"
                  ? "border-blue-500 text-blue-600"
                  : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
              }`}
            >
              <div className="flex items-center gap-2">
                <DollarSign size={18} />
                <span>Balances</span>
              </div>
            </button>
          </nav>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            {activeTab === "activity" && (
              <div className="space-y-4">
                <div className="flex justify-between items-center px-1">
                  <h2 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
                    <Activity size={20} className="text-blue-600" />
                    <span>Shared Activity</span>
                  </h2>
                  <Button
                    size="sm"
                    onClick={() => setIsAddExpenseModalOpen(true)}
                    className="flex items-center gap-2"
                  >
                    <Plus size={18} />
                    <span>Add Expense</span>
                  </Button>
                </div>
                <GroupActivity
                  groupId={Number(id)}
                  balancesResponse={balancesResponse}
                  onAddExpense={() => setIsAddExpenseModalOpen(true)}
                />
              </div>
            )}

            {activeTab === "members" && (
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0">
                  <CardTitle className="flex items-center gap-2">
                    <Users size={20} />
                    <span>Members</span>
                  </CardTitle>
                  <div className="flex items-center gap-2">
                    <span className="text-sm text-gray-500">
                      {group.members.length} members
                    </span>
                    {(isOwner ||
                      isAdmin ||
                      group.allowMembersToManageMembers) && (
                      <button
                        aria-label="Add member"
                        onClick={() => setIsAddMemberModalOpen(true)}
                        className="p-1.5 text-blue-600 hover:text-blue-700 hover:bg-blue-50 rounded-md transition-colors"
                        title="Add Member"
                      >
                        <UserPlus size={16} />
                      </button>
                    )}
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="divide-y divide-gray-100">
                    {group.members.map((member) => {
                      const balanceInfo = balancesResponse?.balances.find(
                        (b) => b.userId === member.userId,
                      );
                      const displayName = balanceInfo
                        ? `${balanceInfo.firstName} ${balanceInfo.lastName}`
                        : `User ${member.userId}`;

                      let roleVariant: BadgeVariant = "member";
                      let roleLabel = "Member";

                      if (member.userId === group.createdBy) {
                        roleVariant = "owner";
                        roleLabel = "Owner";
                      } else if (member.role === "ADMIN") {
                        roleVariant = "admin";
                        roleLabel = "Admin";
                      }

                      const isCurrentUser = member.userId === Number(user?.id);
                      const isFriend = friends?.some(
                        (f) => f.id === member.userId,
                      );
                      const isTempFriend =
                        !isCurrentUser && friends && !isFriend;

                      return (
                        <div
                          key={member.id}
                          className="flex items-center justify-between py-3 first:pt-0 last:pb-0"
                        >
                          <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center text-blue-700 font-medium text-sm">
                              {displayName.charAt(0)}
                            </div>
                            <div className="flex flex-col">
                              <span className="text-sm font-medium text-gray-900">
                                {displayName}
                              </span>
                            </div>
                          </div>
                          <div className="flex items-center gap-2">
                            {isTempFriend && (
                              <Badge variant="temp">Temp Friend</Badge>
                            )}
                            <Badge variant={roleVariant}>{roleLabel}</Badge>

                            {/* Role Management Dropdown */}
                            {(isAdmin || isOwner) &&
                              member.userId !== group.createdBy && (
                                <Dropdown
                                  trigger={
                                    <button
                                      aria-label="Manage role"
                                      className="p-1 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-100"
                                    >
                                      <MoreVertical size={16} />
                                    </button>
                                  }
                                  items={[
                                    {
                                      label:
                                        member.role === "ADMIN"
                                          ? "Demote to Member"
                                          : "Promote to Admin",
                                      onClick: () =>
                                        handleRoleUpdate(
                                          member.userId,
                                          member.role === "ADMIN"
                                            ? "MEMBER"
                                            : "ADMIN",
                                        ),
                                      disabled: updateRoleMutation.isPending,
                                    },
                                  ]}
                                />
                              )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </CardContent>
              </Card>
            )}

            {activeTab === "balances" && <GroupBalances groupId={Number(id)} />}
          </div>

          <div className="space-y-6">
            {isOwner && (
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <Settings size={20} />
                    <span>Group Settings</span>
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    <div className="flex items-center justify-between">
                      <div className="flex flex-col">
                        <span className="text-sm font-medium text-gray-900">
                          Manage Members
                        </span>
                        <span className="text-xs text-gray-500">
                          Allow members to add/remove others
                        </span>
                      </div>
                      <button
                        className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 ${group.allowMembersToManageMembers ? "bg-blue-600" : "bg-gray-200"} ${updateGroupMutation.isPending ? "opacity-50 cursor-not-allowed" : ""}`}
                        onClick={() =>
                          updateGroupMutation.mutate({
                            allowMembersToManageMembers:
                              !group.allowMembersToManageMembers,
                          })
                        }
                        disabled={updateGroupMutation.isPending}
                        aria-label="Toggle allow members to manage members"
                      >
                        <span
                          className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${group.allowMembersToManageMembers ? "translate-x-6" : "translate-x-1"}`}
                        />
                      </button>
                    </div>

                    <div className="flex items-center justify-between">
                      <div className="flex flex-col">
                        <span className="text-sm font-medium text-gray-900">
                          Collaborative Editing
                        </span>
                        <span className="text-xs text-gray-500">
                          Allow members to edit/delete expenses
                        </span>
                      </div>
                      <button
                        className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 ${group.allowMembersToEditExpenses ? "bg-blue-600" : "bg-gray-200"} ${updateGroupMutation.isPending ? "opacity-50 cursor-not-allowed" : ""}`}
                        onClick={() =>
                          updateGroupMutation.mutate({
                            allowMembersToEditExpenses:
                              !group.allowMembersToEditExpenses,
                          })
                        }
                        disabled={updateGroupMutation.isPending}
                        aria-label="Toggle allow members to edit expenses"
                      >
                        <span
                          className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${group.allowMembersToEditExpenses ? "translate-x-6" : "translate-x-1"}`}
                        />
                      </button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            )}

            <Card>
              <CardHeader>
                <CardTitle>Actions</CardTitle>
              </CardHeader>
              <CardContent>
                <Button
                  variant="secondary"
                  onClick={() => setIsLeaveModalOpen(true)}
                  className="w-full flex items-center justify-center gap-2 text-red-600 border-red-200 hover:bg-red-50 hover:text-red-700"
                >
                  <LogOut size={18} />
                  <span>Leave Group</span>
                </Button>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>

      <Modal
        isOpen={isLeaveModalOpen}
        onClose={() => setIsLeaveModalOpen(false)}
        title="Leave Group"
      >
        <div className="space-y-4">
          {isBalancesLoading ? (
            <div className="flex justify-center py-4">
              <Loader2 className="animate-spin text-blue-600" size={24} />
            </div>
          ) : !canLeave ? (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
              You cannot leave this group while you have an outstanding balance
              ({currentUserBalance}).
            </div>
          ) : (
            <p className="text-gray-600">
              Are you sure you want to leave this group? You will no longer be
              able to see expenses or add new ones.
            </p>
          )}
          <div className="flex justify-end gap-3">
            <Button
              variant="ghost"
              onClick={() => setIsLeaveModalOpen(false)}
              disabled={leaveMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={handleLeave}
              disabled={
                leaveMutation.isPending || isBalancesLoading || !canLeave
              }
            >
              {leaveMutation.isPending ? "Leaving..." : "Leave Group"}
            </Button>
          </div>
        </div>
      </Modal>

      <Modal
        isOpen={isSelfDemoteModalOpen}
        onClose={() => setIsSelfDemoteModalOpen(false)}
        title="Confirm Self-Demotion"
      >
        <div className="space-y-4">
          <div className="flex items-start gap-3 p-3 bg-amber-50 border border-amber-200 rounded-lg text-amber-800 text-sm">
            <ShieldAlert className="shrink-0" size={20} />
            <p>
              Are you sure you want to demote yourself to a Member? You will
              lose all administrative privileges in this group.
            </p>
          </div>
          <div className="flex justify-end gap-3">
            <Button
              variant="ghost"
              onClick={() => setIsSelfDemoteModalOpen(false)}
              disabled={updateRoleMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={confirmSelfDemote}
              disabled={updateRoleMutation.isPending}
            >
              {updateRoleMutation.isPending
                ? "Updating..."
                : "Confirm Demotion"}
            </Button>
          </div>
        </div>
      </Modal>

      {group && (
        <AddMemberModal
          isOpen={isAddMemberModalOpen}
          onClose={() => setIsAddMemberModalOpen(false)}
          group={group}
        />
      )}

      {group && (
        <CreateExpenseModal
          isOpen={isAddExpenseModalOpen}
          onClose={() => setIsAddExpenseModalOpen(false)}
          group={group}
        />
      )}
    </DashboardLayout>
  );
};

export default GroupDetails;
