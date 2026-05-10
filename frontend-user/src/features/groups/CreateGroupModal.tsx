import React, { useState } from "react";
import axios, { AxiosError } from "axios";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2, UserPlus, CheckCircle2 } from "lucide-react";
import Modal from "../../components/core/Modal/Modal";
import Input from "../../components/core/Input/Input";
import Button from "../../components/core/Button/Button";
import { groupService } from "./groupService";
import api from "../../lib/axios";
import { useAuthStore } from "../../store/authStore";
import type { User } from "../../types/user";

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
  const currentUser = useAuthStore((state) => state.user);

  const { data: friends, isLoading: isLoadingFriends } = useQuery({
    queryKey: ["friends", currentUser?.id],
    queryFn: async () => {
      if (!currentUser?.id) return [];
      const response = await api.get<User[]>(
        `/users/${currentUser.id}/friends`,
      );
      return response.data;
    },
    enabled: isOpen && !!currentUser?.id,
  });

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
    <Modal isOpen={isOpen} onClose={onClose} title="Create New Group">
      <form onSubmit={handleSubmit} className="space-y-6">
        <div className="space-y-4">
          <Input
            label="Group Name"
            placeholder="e.g. Summer Trip 2025"
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              if (e.target.value) setNameError("");
            }}
            error={nameError}
          />
          <Input
            label="Description"
            placeholder="What is this group for?"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>

        <div>
          <label className="text-sm font-medium text-gray-700 mb-2 block">
            Add Members (Friends)
          </label>
          <div className="max-h-48 overflow-y-auto border border-gray-200 rounded-lg p-2 space-y-1">
            {isLoadingFriends ? (
              <div className="flex justify-center py-4">
                <Loader2 className="animate-spin text-blue-600" size={20} />
              </div>
            ) : friends && friends.length > 0 ? (
              friends.map((friend) => (
                <div
                  key={friend.id}
                  onClick={() => toggleFriend(friend.id)}
                  className={`flex items-center justify-between p-2 rounded-md cursor-pointer transition-colors ${
                    selectedFriends.includes(friend.id)
                      ? "bg-blue-50 border-blue-100 border"
                      : "hover:bg-gray-50"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 text-xs font-bold">
                      {friend.firstName[0]}
                      {friend.lastName ? friend.lastName[0] : ""}
                    </div>
                    <span className="text-sm font-medium text-gray-700">
                      {friend.firstName} {friend.lastName}
                    </span>
                  </div>
                  {selectedFriends.includes(friend.id) ? (
                    <CheckCircle2 size={18} className="text-blue-600" />
                  ) : (
                    <UserPlus size={18} className="text-gray-400" />
                  )}
                </div>
              ))
            ) : (
              <p className="text-sm text-gray-500 text-center py-4">
                No friends found. Add friends first!
              </p>
            )}
          </div>
        </div>

        {error && (
          <div className="p-3 bg-red-50 border border-red-100 rounded-md">
            <p className="text-sm text-red-600 font-medium">{error}</p>
          </div>
        )}

        <div className="flex gap-3 pt-2">
          <Button
            type="button"
            variant="ghost"
            className="flex-1 border border-gray-300"
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
    </Modal>
  );
};

export default CreateGroupModal;
