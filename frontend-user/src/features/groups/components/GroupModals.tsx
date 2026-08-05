import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Loader2, ShieldAlert, Trash2 } from "lucide-react";
import AddMemberModal from "../AddMemberModal";
import ExpenseModal from "../../expenses/ExpenseModal";
import type { GroupDetailsSession } from "../useGroupDetailsSession";

interface GroupModalsProps {
  session: GroupDetailsSession;
}

export function GroupModals({ session }: GroupModalsProps) {
  const {
    group,
    governance,
    isBalancesLoading,
    isSettlementsLoading,
    isLeaveModalOpen,
    setIsLeaveModalOpen,
    handleLeave,
    leaveMutation,
    isSelfDemoteModalOpen,
    setIsSelfDemoteModalOpen,
    confirmSelfDemote,
    updateRoleMutation,
    isDeleteModalOpen,
    setIsDeleteModalOpen,
    expenseToDelete,
    confirmDeleteExpense,
    deleteExpenseMutation,
    isAddMemberModalOpen,
    setIsAddMemberModalOpen,
    isExpenseModalOpen,
    setIsExpenseModalOpen,
    editingExpense,
    setEditingExpense,
  } = session;

  const canLeave = governance.canLeave;

  return (
    <>
      <Dialog
        open={isLeaveModalOpen}
        onOpenChange={(open) => {
          if (!open) setIsLeaveModalOpen(false);
        }}
      >
        <DialogContent className="max-w-lg bg-white">
          <DialogHeader className="border-b border-border pb-3">
            <DialogTitle className="text-xl font-semibold text-foreground">
              Leave Group
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-4 pt-2">
            {isBalancesLoading || isSettlementsLoading ? (
              <div className="flex justify-center py-4">
                <Loader2 className="animate-spin text-blue-600" size={24} />
              </div>
            ) : !canLeave ? (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                {governance.leaveReason}
              </div>
            ) : (
              <p className="text-muted-foreground">
                Are you sure you want to leave this group? You will no longer be
                able to see expenses or add new ones.
              </p>
            )}
            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="outline"
                onClick={() => setIsLeaveModalOpen(false)}
                disabled={leaveMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="destructive"
                onClick={handleLeave}
                disabled={
                  leaveMutation.isPending ||
                  isBalancesLoading ||
                  isSettlementsLoading ||
                  !canLeave
                }
              >
                {leaveMutation.isPending ? "Leaving..." : "Leave Group"}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog
        open={isSelfDemoteModalOpen}
        onOpenChange={(open) => {
          if (!open) setIsSelfDemoteModalOpen(false);
        }}
      >
        <DialogContent className="max-w-lg bg-white">
          <DialogHeader className="border-b border-border pb-3">
            <DialogTitle className="text-xl font-semibold text-foreground">
              Confirm Self-Demotion
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-4 pt-2">
            <div className="flex items-start gap-3 p-3 bg-amber-50 border border-amber-200 rounded-lg text-amber-800 text-sm">
              <ShieldAlert className="shrink-0" size={20} />
              <p>
                Are you sure you want to demote yourself to a Member? You will
                lose all administrative privileges in this group.
              </p>
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="outline"
                onClick={() => setIsSelfDemoteModalOpen(false)}
                disabled={updateRoleMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="default"
                onClick={confirmSelfDemote}
                disabled={updateRoleMutation.isPending}
              >
                {updateRoleMutation.isPending
                  ? "Updating..."
                  : "Confirm Demotion"}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog
        open={isDeleteModalOpen}
        onOpenChange={(open) => {
          if (!open) setIsDeleteModalOpen(false);
        }}
      >
        <DialogContent className="max-w-lg bg-white">
          <DialogHeader className="border-b border-border pb-3">
            <DialogTitle className="text-xl font-semibold text-foreground">
              Delete Expense
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-4 pt-2">
            <p className="text-muted-foreground">
              Are you sure you want to delete "
              <span className="font-semibold text-foreground">
                {expenseToDelete?.description}
              </span>
              "? This action cannot be undone and will update everyone's balances.
            </p>
            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="outline"
                onClick={() => setIsDeleteModalOpen(false)}
                disabled={deleteExpenseMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="destructive"
                onClick={confirmDeleteExpense}
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

      {group && (
        <AddMemberModal
          isOpen={isAddMemberModalOpen}
          onClose={() => setIsAddMemberModalOpen(false)}
          group={group}
        />
      )}

      {group && isExpenseModalOpen && (
        <ExpenseModal
          key={editingExpense?.id || "new"}
          isOpen={isExpenseModalOpen}
          onClose={() => {
            setIsExpenseModalOpen(false);
            setEditingExpense(undefined);
          }}
          group={group}
          expense={editingExpense}
        />
      )}
    </>
  );
}
