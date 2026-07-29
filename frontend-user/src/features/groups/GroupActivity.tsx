import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { expenseService } from "../expenses/expenseService";
import { groupService } from "./groupService";
import { useAuthStore } from "../../store/authStore";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { toast } from "sonner";
import { useDisplayNames } from "../../hooks/useDisplayName";
import {
  Loader2,
  Receipt,
  Plus,
  Edit2,
  MoreVertical,
  Trash2,
  PlusCircle,
  XCircle,
} from "lucide-react";
import type { Group } from "../../types/group";
import type { Expense } from "../../types/expense";
import { ActivityLogType } from "../../types/activity";
import type { ActivityLog } from "../../types/activity";
import { useState, useMemo } from "react";

interface GroupActivityProps {
  groupId: number;
  onAddExpense?: () => void;
  onEditExpense?: (expense: Expense) => void;
  group?: Group;
}

const GroupActivity = ({
  groupId,
  onAddExpense,
  onEditExpense,
  group,
}: GroupActivityProps) => {
  const { user } = useAuthStore();
  const currentUserId = Number(user?.id);
  const queryClient = useQueryClient();


  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [expenseToDelete, setExpenseToDelete] = useState<Expense | null>(null);

  const { data: activities, isLoading: isLoadingActivity } = useQuery({
    queryKey: ["group-activity", groupId],
    queryFn: () => groupService.getGroupActivity(groupId),
  });

  const { data: expenses } = useQuery({
    queryKey: ["expenses", groupId],
    queryFn: () => expenseService.getGroupExpenses(groupId),
  });

  const deleteExpenseMutation = useMutation({
    mutationFn: (expenseId: number) =>
      expenseService.deleteExpense(groupId, expenseId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-activity", groupId] });
      queryClient.invalidateQueries({ queryKey: ["expenses", groupId] });
      queryClient.invalidateQueries({ queryKey: ["group-balances", groupId] });
      toast.success("Expense deleted successfully");
      setIsDeleteModalOpen(false);
      setExpenseToDelete(null);
    },
    onError: () => {
      toast.error("Failed to delete expense");
    },
  });

  const actorIds = useMemo(
    () => (activities ? [...new Set(activities.map((a: ActivityLog) => a.actorId))] : []),
    [activities],
  );
  const nameMap = useDisplayNames(actorIds);

  const canManageExpense = (expenseId: number) => {
    if (!group || !expenses) return false;
    const expense = expenses.find((e) => e.id === expenseId);
    if (!expense) return false;

    const member = group.members.find((m) => m.userId === currentUserId);
    if (!member) return false;

    const isAdmin = member.role === "ADMIN" || group.createdBy === currentUserId;
    const isPayer = expense.paidBy === currentUserId;

    return isAdmin || isPayer || group.allowMembersToEditExpenses;
  };

  const handleDeleteClick = (expenseId: number, description: string) => {
    const expense = expenses?.find((e) => e.id === expenseId);
    if (expense) {
      setExpenseToDelete(expense);
    } else {
      // Create a dummy expense for the modal description if it's already deleted or not found
      setExpenseToDelete({ id: expenseId, description } as Expense);
    }
    setIsDeleteModalOpen(true);
  };

  const confirmDelete = () => {
    if (expenseToDelete) {
      deleteExpenseMutation.mutate(expenseToDelete.id);
    }
  };

  return (
    <div className="space-y-4">
      {isLoadingActivity ? (
        <div className="flex justify-center p-8">
          <Loader2 className="animate-spin text-blue-600" />
        </div>
      ) : !activities || activities.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center">
            <Receipt className="mx-auto text-muted-foreground mb-4 opacity-50" size={48} />
            <h3 className="text-lg font-medium text-foreground mb-1">
              No activity yet
            </h3>
            <p className="text-muted-foreground italic mb-6">
              Add an expense or record a payment to get started!
            </p>
            {onAddExpense && (
              <Button
                onClick={onAddExpense}
                className="flex items-center gap-2 mx-auto"
              >
                <Plus size={18} />
                <span>Add Expense</span>
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <ScrollArea className="h-[500px] pr-4">
          <div className="space-y-4">
            {activities.map((activity: ActivityLog) => {
              const actorName = nameMap[activity.actorId] ?? `User ${activity.actorId}`;
          const date = new Date(activity.timestamp);

          let Icon = Receipt;
          let iconBg = "bg-muted text-foreground";
          let title = "";
          let description = "";

          switch (activity.type) {
            case ActivityLogType.EXPENSE_CREATED: {
              Icon = PlusCircle;
              iconBg = "bg-green-100 text-green-700";
              title = `${actorName} added "${activity.entityName}"`;
              description = `on ${date.toLocaleDateString()} at ${date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
              break;
            }
            case ActivityLogType.EXPENSE_DELETED: {
              Icon = XCircle;
              iconBg = "bg-red-100 text-red-700";
              title = `${actorName} deleted "${activity.entityName}"`;
              description = `on ${date.toLocaleDateString()} at ${date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
              break;
            }
            case ActivityLogType.EXPENSE_UPDATED: {
              Icon = Edit2;
              iconBg = "bg-blue-100 text-blue-700";
              title = `${actorName} updated "${activity.entityName}"`;
              const diffText = activity.details
                ? ` (${activity.details.replace(/; $/, "")})`
                : "";
              description = `on ${date.toLocaleDateString()} at ${date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}${diffText}`;
              break;
            }
          }

          const expense = expenses?.find((e) => e.id === activity.entityId);
          const canManage =
            activity.type !== ActivityLogType.EXPENSE_DELETED &&
            canManageExpense(activity.entityId);

          const dropdownItems = [
            ...(onEditExpense && expense
              ? [
                  {
                    label: "Edit",
                    onClick: () => onEditExpense(expense),
                    icon: <Edit2 size={14} />,
                  },
                ]
              : []),
            {
              label: "Delete",
              onClick: () =>
                handleDeleteClick(activity.entityId, activity.entityName),
              variant: "danger" as const,
              icon: <Trash2 size={14} />,
            },
          ];

          return (
            <Card
              key={`activity-${activity.id}`}
              className="hover:shadow-md transition-shadow"
            >
              <CardContent className="p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4 flex-1">
                    <div className={`p-2 rounded-full ${iconBg}`}>
                      <Icon size={20} />
                    </div>
                    <div className="flex-1">
                      <h3 className="font-semibold text-foreground">{title}</h3>
                      <p className="text-sm text-muted-foreground">{description}</p>
                    </div>
                  </div>
                  {canManage && (
                    <DropdownMenu>
                      <DropdownMenuTrigger
                        className="p-1 hover:bg-muted rounded-full text-muted-foreground hover:text-foreground transition-colors"
                        aria-label={`Actions for ${activity.entityName}`}
                      >
                        <MoreVertical size={20} />
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {dropdownItems.map((item, index) => (
                          <DropdownMenuItem
                            key={index}
                            onClick={item.onClick}
                            variant={item.variant === "danger" ? "destructive" : "default"}
                            className="flex items-center gap-1.5"
                          >
                            {item.icon}
                            <span>{item.label}</span>
                          </DropdownMenuItem>
                        ))}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  )}
                </div>
              </CardContent>
            </Card>
          );
        })}
          </div>
        </ScrollArea>
      )}

      <Dialog open={isDeleteModalOpen} onOpenChange={(open) => { if (!open) setIsDeleteModalOpen(false); }}>
        <DialogContent className="max-w-lg bg-background">
          <DialogHeader className="border-b border-border pb-3">
            <DialogTitle className="text-xl font-semibold text-foreground">Delete Expense</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 pt-2">
            <p className="text-muted-foreground">
              Are you sure you want to delete this expense? This will remove the expense for all group members and recalculate balances.
              <span className="font-semibold text-foreground">
                {expenseToDelete?.description}
              </span>
              "? This action cannot be undone and will update everyone's balances.
            </p>
            <div className="flex justify-end gap-3">
              <Button
                variant="secondary"
                onClick={() => setIsDeleteModalOpen(false)}
                disabled={deleteExpenseMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="destructive"
                onClick={confirmDelete}
                disabled={deleteExpenseMutation.isPending}
                className="flex items-center gap-2"
              >
                {deleteExpenseMutation.isPending ? (
                  <>
                    <Loader2 size={18} className="animate-spin" />
                    <span>Deleting...</span>
                  </>
                ) : (
                  <>
                    <Trash2 size={18} />
                    <span>Delete Expense</span>
                  </>
                )}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default GroupActivity;
