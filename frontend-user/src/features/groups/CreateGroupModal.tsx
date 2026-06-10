import React, { useState } from "react";
import axios, { AxiosError } from "axios";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldLabel, FieldError } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { groupService } from "./groupService";
import MemberPicker from "./MemberPicker";

interface CreateGroupModalProps {
  isOpen: boolean;
  onClose: () => void;
}

interface ApiError {
  message: string;
}

const CreateGroupModal = ({ isOpen, onClose }: CreateGroupModalProps) => {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [selectedFriends, setSelectedFriends] = useState<number[]>([]);
  const [error, setError] = useState("");
  const [nameError, setNameError] = useState("");

  const queryClient = useQueryClient();

  const createGroupMutation = useMutation({
    mutationFn: async () => {
      if (!name.trim()) {
        setNameError("Group name is required");
        throw new Error("Name required");
      }

      return await groupService.createGroup({
        name,
        description,
        memberUserIds: selectedFriends,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      resetForm();
      onClose();
    },
    onError: (err: unknown) => {
      if (axios.isAxiosError(err)) {
        const axiosError = err as AxiosError<ApiError>;
        if (axiosError.message !== "Name required") {
          setError(
            axiosError.response?.data?.message || "Failed to create group",
          );
        }
      } else if (err instanceof Error) {
        if (err.message !== "Name required") {
          setError(err.message);
        }
      } else {
        setError("Failed to create group");
      }
    },
  });

  const resetForm = () => {
    setName("");
    setDescription("");
    setSelectedFriends([]);
    setError("");
    setNameError("");
  };

  const toggleFriend = (id: number) => {
    setSelectedFriends((prev) =>
      prev.includes(id) ? prev.filter((fid) => fid !== id) : [...prev, id],
    );
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setNameError("");
    setError("");
    createGroupMutation.mutate();
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="max-w-lg bg-white">
        <DialogHeader className="border-b border-gray-200 pb-3">
          <DialogTitle className="text-xl font-semibold text-gray-900">Create New Group</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-6 pt-2">
          <div className="space-y-4">
            <Field>
              <FieldLabel htmlFor="group-name">Group Name</FieldLabel>
              <Input
                id="group-name"
                placeholder="e.g. Summer Trip 2025"
                value={name}
                onChange={(e) => {
                  setName(e.target.value);
                  if (e.target.value) setNameError("");
                }}
              />
              {nameError && <FieldError>{nameError}</FieldError>}
            </Field>

            <Field>
              <FieldLabel htmlFor="group-description">Description</FieldLabel>
              <Input
                id="group-description"
                placeholder="What is this group for?"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </Field>
          </div>

          <div>
            <label className="text-sm font-medium text-gray-700 mb-2 block">
              Add Members
            </label>
            <MemberPicker
              selectedIds={selectedFriends}
              onToggle={toggleFriend}
              placeholder="Search friends or system users..."
            />
          </div>

          {error && (
            <div className="p-3 bg-red-50 border border-red-100 rounded-md">
              <p className="text-sm text-red-600 font-medium">{error}</p>
            </div>
          )}

          <div className="flex gap-3 pt-2">
            <Button
              type="button"
              variant="outline"
              className="flex-1"
              onClick={() => {
                resetForm();
                onClose();
              }}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              className="flex-1"
              disabled={createGroupMutation.isPending}
            >
              {createGroupMutation.isPending ? (
                <>
                  <Loader2 size={18} className="animate-spin mr-2" />
                  Creating...
                </>
              ) : (
                "Create Group"
              )}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
};

export default CreateGroupModal;
