import React, { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { groupService } from "./groupService";
import { toast } from "sonner";
import MemberPicker from "./MemberPicker";
import type { Group } from "../../types/group";

interface AddMemberModalProps {
  isOpen: boolean;
  onClose: () => void;
  group: Group;
}

const AddMemberModal = ({ isOpen, onClose, group }: AddMemberModalProps) => {
  const [selectedUserIds, setSelectedUserIds] = useState<number[]>([]);
  const queryClient = useQueryClient();


  const bulkAddMutation = useMutation({
    mutationFn: () => groupService.bulkAddMembers(group.id, selectedUserIds),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group", group.id] });
      queryClient.invalidateQueries({ queryKey: ["group-balances", group.id] });
      toast.success("Members added successfully");
      setSelectedUserIds([]);
      onClose();
    },
    onError: () => {
      toast.error("Failed to add members");
    },
  });

  const toggleUser = (id: number) => {
    setSelectedUserIds((prev) =>
      prev.includes(id) ? prev.filter((uid) => uid !== id) : [...prev, id],
    );
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedUserIds.length === 0) return;
    bulkAddMutation.mutate();
  };

  const handleClose = () => {
    setSelectedUserIds([]);
    onClose();
  };

  const existingMemberIds = group.members.map((m) => m.userId);

  return (
    <Dialog open={isOpen} onOpenChange={(open) => { if (!open) handleClose(); }}>
      <DialogContent className="max-w-lg bg-white">
        <DialogHeader className="border-b border-gray-200 pb-3">
          <DialogTitle className="text-xl font-semibold text-gray-900">Add Members</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4 pt-2">
          <div>
            <label className="text-sm font-medium text-gray-700 mb-2 block">
              Select People to Add
            </label>
            <MemberPicker
              selectedIds={selectedUserIds}
              onToggle={toggleUser}
              excludeIds={existingMemberIds}
              placeholder="Search friends or system users..."
            />
          </div>

          <div className="flex gap-3 pt-2">
            <Button
              type="button"
              variant="outline"
              className="flex-1"
              onClick={handleClose}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              className="flex-1"
              disabled={selectedUserIds.length === 0 || bulkAddMutation.isPending}
            >
              {bulkAddMutation.isPending ? (
                <>
                  <Loader2 size={18} className="animate-spin mr-2" />
                  Adding...
                </>
              ) : (
                `Add ${selectedUserIds.length > 0 ? selectedUserIds.length : ""} Member${selectedUserIds.length !== 1 ? "s" : ""}`
              )}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
};

export default AddMemberModal;
