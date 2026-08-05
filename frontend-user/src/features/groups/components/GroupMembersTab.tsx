import { Link } from "react-router-dom";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import { TabsContent } from "@/components/ui/tabs";
import { Users, UserPlus, MoreVertical, Settings, LogOut } from "lucide-react";
import type { Group } from "../../types/group";
import type { GroupGovernance } from "../membershipGating";
import type { Friend } from "../../types/friend";
import type { BalancesResponse } from "../groupService";
import type { UseMutationResult } from "@tanstack/react-query";

interface GroupMembersTabProps {
  group: Group;
  userId?: number;
  memberNames: Record<number, string>;
  friends?: Friend[];
  balancesResponse?: BalancesResponse;
  isBalancesLoading: boolean;
  governance: GroupGovernance;
  onOpenAddMemberModal: () => void;
  onRoleUpdate: (userId: number, role: "ADMIN" | "MEMBER") => void;
  onOpenLeaveModal: () => void;
  updateRoleMutationPending: boolean;
  updateGroupMutation: UseMutationResult<
    Group,
    Error,
    Partial<Group>
  >;
}

export function GroupMembersTab({
  group,
  userId,
  memberNames,
  friends,
  balancesResponse,
  isBalancesLoading,
  governance,
  onOpenAddMemberModal,
  onRoleUpdate,
  onOpenLeaveModal,
  updateRoleMutationPending,
  updateGroupMutation,
}: GroupMembersTabProps) {
  const currentUserRole = governance.currentUserRole;

  return (
    <TabsContent
      value="members"
      className="h-full overflow-y-auto pr-1 space-y-4 pb-2"
    >
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0">
          <CardTitle className="flex items-center gap-2">
            <Users size={20} />
            <span>Members</span>
          </CardTitle>
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">
              {group.members.length} members
            </span>
            {governance.canManageMembers && (
              <button
                aria-label="Add member"
                onClick={onOpenAddMemberModal}
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
              const displayName =
                memberNames[member.userId] ?? `User ${member.userId}`;

              let badgeClassName =
                "bg-muted text-foreground border-border hover:bg-muted/80";
              let roleLabel = "Member";

              if (member.userId === group.createdBy) {
                badgeClassName =
                  "bg-purple-100 text-purple-800 border-purple-200 hover:bg-purple-100/80";
                roleLabel = "Owner";
              } else if (member.role === "ADMIN") {
                badgeClassName =
                  "bg-blue-100 text-blue-800 border-blue-200 hover:bg-blue-100/80";
                roleLabel = "Admin";
              }

              const isCurrentUser = member.userId === userId;
              const isFriend = friends?.some((f) => f.id === member.userId);
              const isTempFriend = !isCurrentUser && friends && !isFriend;

              const debtToMember = balancesResponse?.simplifiedDebts.find(
                (d) => d.from === userId && d.to === member.userId,
              );
              const debtFromMember = balancesResponse?.simplifiedDebts.find(
                (d) => d.to === userId && d.from === member.userId,
              );

              const memberInfoContent = (
                <>
                  <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center text-blue-700 font-medium text-sm shrink-0">
                    {displayName.charAt(0)}
                  </div>
                  <div className="flex flex-col text-left">
                    <span className="text-sm font-medium text-foreground group-hover:text-blue-600 transition-colors">
                      {displayName}
                    </span>
                    {!isCurrentUser && (
                      <span className="text-xs">
                        {isBalancesLoading ? (
                          <span className="text-muted-foreground font-normal animate-pulse">
                            loading balance...
                          </span>
                        ) : debtToMember ? (
                          <span className="text-red-500 font-medium">
                            you owe ${debtToMember.amount.toFixed(2)}
                          </span>
                        ) : debtFromMember ? (
                          <span className="text-green-600 font-medium">
                            owes you ${debtFromMember.amount.toFixed(2)}
                          </span>
                        ) : (
                          <span className="text-muted-foreground font-normal">
                            settled up
                          </span>
                        )}
                      </span>
                    )}
                  </div>
                </>
              );

              return (
                <div
                  key={member.id}
                  className="flex items-center justify-between py-3 first:pt-0 last:pb-0"
                >
                  {isCurrentUser ? (
                    <div className="flex items-center gap-3">
                      {memberInfoContent}
                    </div>
                  ) : (
                    <Link
                      to={`/friends/${member.userId}`}
                      className="flex items-center gap-3 hover:opacity-80 transition-opacity group cursor-pointer"
                    >
                      {memberInfoContent}
                    </Link>
                  )}
                  <div className="flex items-center gap-2">
                    {isTempFriend && (
                      <Badge className="bg-orange-100 text-orange-800 border-orange-200 hover:bg-orange-100/80">
                        Temp Friend
                      </Badge>
                    )}
                    <Badge className={badgeClassName}>{roleLabel}</Badge>

                    {/* Role Management Dropdown */}
                    {governance.canManageRoleFor(member.userId) && (
                      <DropdownMenu>
                        <DropdownMenuTrigger
                          className="p-1 text-muted-foreground hover:text-muted-foreground rounded-full hover:bg-muted"
                          aria-label="Manage role"
                        >
                          <MoreVertical size={16} />
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          {member.role === "ADMIN" &&
                            governance.canDemoteMember(member.userId) && (
                              <DropdownMenuItem
                                disabled={updateRoleMutationPending}
                                onClick={() =>
                                  onRoleUpdate(member.userId, "MEMBER")
                                }
                              >
                                Demote to Member
                              </DropdownMenuItem>
                            )}
                          {member.role === "MEMBER" &&
                            governance.canPromoteMember(member.userId) && (
                              <DropdownMenuItem
                                disabled={updateRoleMutationPending}
                                onClick={() =>
                                  onRoleUpdate(member.userId, "ADMIN")
                                }
                              >
                                Promote to Admin
                              </DropdownMenuItem>
                            )}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </CardContent>
      </Card>

      {/* Relocated Group Settings */}
      {currentUserRole === "OWNER" && (
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
                  <span className="text-sm font-medium text-foreground">
                    Manage Members
                  </span>
                  <span className="text-xs text-muted-foreground">
                    Allow members to add/remove others
                  </span>
                </div>
                <Switch
                  checked={group.allowMembersToManageMembers}
                  onCheckedChange={(checked) =>
                    updateGroupMutation.mutate({
                      allowMembersToManageMembers: checked,
                    })
                  }
                  disabled={updateGroupMutation.isPending}
                  aria-label="Toggle allow members to manage members"
                />
              </div>

              <div className="flex items-center justify-between">
                <div className="flex flex-col">
                  <span className="text-sm font-medium text-foreground">
                    Collaborative Editing
                  </span>
                  <span className="text-xs text-muted-foreground">
                    Allow members to edit/delete expenses
                  </span>
                </div>
                <Switch
                  checked={group.allowMembersToEditExpenses}
                  onCheckedChange={(checked) =>
                    updateGroupMutation.mutate({
                      allowMembersToEditExpenses: checked,
                    })
                  }
                  disabled={updateGroupMutation.isPending}
                  aria-label="Toggle allow members to edit expenses"
                />
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Relocated Actions */}
      <Card>
        <CardHeader>
          <CardTitle>Actions</CardTitle>
        </CardHeader>
        <CardContent>
          <Button
            variant="outline"
            onClick={onOpenLeaveModal}
            className="w-full flex items-center justify-center gap-2 text-red-600 border-red-200 hover:bg-red-50 hover:text-red-700"
          >
            <LogOut size={18} />
            <span>Leave Group</span>
          </Button>
        </CardContent>
      </Card>
    </TabsContent>
  );
}
