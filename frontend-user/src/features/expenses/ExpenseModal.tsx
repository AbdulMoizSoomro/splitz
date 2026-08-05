import React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Checkbox } from "@/components/ui/checkbox";
import { expenseService } from "./expenseService";
import { categoryService } from "./categoryService";
import { useExpenseForm } from "./expenseFormEngine";
import type { Group } from "../../types/group";
import type {
  CreateExpenseRequest,
  UpdateExpenseRequest,
  Expense,
} from "../../types/expense";
import { useAuthStore } from "../../store/authStore";
import { useDisplayNames } from "../../hooks/useDisplayName";
import { Loader2, AlertCircle } from "lucide-react";
import { useMemo } from "react";

interface ExpenseModalProps {
  isOpen: boolean;
  onClose: () => void;
  group: Group;
  expense?: Expense;
}

const ExpenseModal = ({
  isOpen,
  onClose,
  group,
  expense,
}: ExpenseModalProps) => {
  const isEditing = !!expense;
  const currentUser = useAuthStore((state) => state.user);
  const queryClient = useQueryClient();

  const form = useExpenseForm({
    group,
    currentUserId: parseInt(currentUser?.id || "0"),
    expense,
  });

  const memberIds = useMemo(() => group.members.map((m) => m.userId), [group.members]);
  const memberNames = useDisplayNames(memberIds);

  const { data: categories } = useQuery({
    queryKey: ["categories"],
    queryFn: categoryService.getCategories,
    enabled: isOpen,
  });

  const createMutation = useMutation({
    mutationFn: (data: CreateExpenseRequest) =>
      expenseService.createExpense(group.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["expenses", group.id] });
      queryClient.invalidateQueries({ queryKey: ["group-activity", group.id] });
      queryClient.invalidateQueries({
        queryKey: ["group-balances", group.id],
      });
      onClose();
      form.resetForm();
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: UpdateExpenseRequest) =>
      expenseService.updateExpense(group.id, expense!.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["expenses", group.id] });
      queryClient.invalidateQueries({ queryKey: ["group-activity", group.id] });
      queryClient.invalidateQueries({
        queryKey: ["group-balances", group.id],
      });
      onClose();
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.isReadyToSubmit) return;

    if (isEditing) {
      updateMutation.mutate(form.getUpdatePayload());
    } else {
      createMutation.mutate(form.getCreatePayload());
    }
  };

  const isPending = createMutation.isPending || updateMutation.isPending;
  const isError = createMutation.isError || updateMutation.isError;

  return (
    <Dialog open={isOpen} onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="max-w-lg bg-background text-foreground border-border">
        <DialogHeader className="border-b border-border pb-3">
          <DialogTitle className="text-xl font-semibold">
            {isEditing ? "Edit Expense" : "Add New Expense"}
          </DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} noValidate className="space-y-4 pt-2">
          <Field>
            <FieldLabel htmlFor="description">Description</FieldLabel>
            <Input
              id="description"
              value={form.state.description}
              onChange={(e) => form.setDescription(e.target.value)}
              placeholder="e.g., Dinner, Groceries"
              required
            />
          </Field>

          <div className="grid grid-cols-2 gap-4">
            <Field>
              <FieldLabel htmlFor="amount">Amount</FieldLabel>
              <Input
                id="amount"
                type="number"
                step="0.01"
                value={form.state.amount}
                onChange={(e) => form.setAmount(e.target.value)}
                placeholder="0.00"
                required
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="date">Date</FieldLabel>
              <Input
                id="date"
                type="date"
                value={form.state.expenseDate}
                onChange={(e) => form.setExpenseDate(e.target.value)}
                required
              />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Field>
              <FieldLabel htmlFor="paidBy">Paid By</FieldLabel>
              <Select
                value={form.state.paidBy.toString()}
                onValueChange={(val) => form.setPaidBy(parseInt(val || "0"))}
              >
                <SelectTrigger id="paidBy" className="w-full">
                  <SelectValue placeholder="Select Payer">{form.state.paidBy === parseInt(currentUser?.id || "0") ? "You" : memberNames[form.state.paidBy] ?? `User ${form.state.paidBy}`}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {group.members.map((member) => (
                    <SelectItem key={member.userId} value={member.userId.toString()}>
                      {member.userId === parseInt(currentUser?.id || "0")
                        ? "You"
                        : memberNames[member.userId] ?? `User ${member.userId}`}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>

            <Field>
              <FieldLabel htmlFor="category">Category</FieldLabel>
              <Select
                value={form.state.categoryId?.toString() || ""}
                onValueChange={(val) => form.setCategoryId(val ? parseInt(val) : undefined)}
              >
                <SelectTrigger id="category" className="w-full">
                  <SelectValue placeholder="Select a category" />
                </SelectTrigger>
                <SelectContent>
                  {categories?.map((cat) => (
                    <SelectItem key={cat.id} value={cat.id.toString()}>
                      {cat.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
          </div>

          <div className="space-y-2 py-2 border-y border-border">
            <span className="text-sm font-medium text-foreground">Split Type:</span>
            <RadioGroup
              value={form.state.splitType}
              onValueChange={(val) => form.setSplitType(val as CreateExpenseRequest["splitType"])}
              className="flex flex-wrap gap-x-4 gap-y-2"
            >
              {(
                ["EQUAL", "EXACT", "PERCENTAGE", "SHARES", "ADJUSTMENT"] as const
              ).map((type) => (
                <div key={type} className="flex items-center gap-1.5">
                  <RadioGroupItem
                    value={type}
                    id={`split-type-${type}`}
                  />
                  <label
                    htmlFor={`split-type-${type}`}
                    className="text-sm text-muted-foreground hover:text-foreground capitalize cursor-pointer"
                  >
                    {form.formatters.splitTypeLabel(type)}
                  </label>
                </div>
              ))}
            </RadioGroup>
          </div>

          <div>
            <label className="block text-sm font-medium text-foreground mb-2">
              Split between members
            </label>
            <div className="max-h-60 overflow-y-auto border border-border rounded-md p-2 space-y-3">
              {group.members.map((member) => (
                <div
                  key={member.userId}
                  className="flex flex-col gap-2 p-2 rounded-md hover:bg-muted/50"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Checkbox
                        id={`member-${member.userId}`}
                        checked={form.state.selectedMembers.includes(member.userId)}
                        onCheckedChange={() => form.toggleMember(member.userId)}
                      />
                      <label
                        htmlFor={`member-${member.userId}`}
                        className="text-sm text-foreground cursor-pointer select-none"
                      >
                        {memberNames[member.userId] ?? `User ${member.userId}`}
                      </label>
                    </div>
                    {form.state.splitType !== "EQUAL" &&
                      form.state.selectedMembers.includes(member.userId) && (
                        <div className="flex items-center gap-1 w-32">
                          <span className="text-sm text-muted-foreground">
                            {form.formatters.unitPrefix(form.state.splitType)}
                          </span>
                          <Input
                            id={`split-value-${member.userId}`}
                            type="number"
                            step={form.state.splitType === "SHARES" ? "1" : "0.01"}
                            value={form.state.splitValues[member.userId] || ""}
                            onChange={(e) =>
                              form.setSplitValue(
                                member.userId,
                                e.target.value,
                              )
                            }
                            placeholder={form.formatters.placeholder(form.state.splitType)}
                            aria-label={`${memberNames[member.userId] ?? `User ${member.userId}`} split value`}
                          />
                          <span className="text-xs text-muted-foreground whitespace-nowrap">
                            {form.formatters.unitSuffix(form.state.splitType)}
                          </span>
                        </div>
                      )}
                  </div>
                </div>
              ))}
            </div>
            {form.state.selectedMembers.length === 0 && (
              <p className="mt-1 text-xs text-red-500 flex items-center gap-1">
                <AlertCircle size={12} />
                Select at least one member to split with.
              </p>
            )}
          </div>

          {form.state.amount && form.state.selectedMembers.length > 0 && (
            <div
              className={`p-3 rounded-md ${form.validation.isValid ? "bg-blue-50 dark:bg-blue-900/20" : "bg-orange-50 dark:bg-orange-900/20"}`}
            >
              {form.state.splitType === "EQUAL" ? (
                <p className="text-sm text-blue-700 dark:text-blue-400">
                  Each person pays:{" "}
                  <span className="font-bold">${form.sharePerPerson}</span>
                </p>
              ) : (
                <div className="flex justify-between items-center text-sm">
                  <p
                    className={
                      form.validation.isValid ? "text-blue-700 dark:text-blue-400" : "text-orange-700 dark:text-orange-400"
                    }
                  >
                    {form.validation.message}
                  </p>
                  {form.state.splitType === "PERCENTAGE" && form.validation.isValid && (
                    <p className="text-blue-700 dark:text-blue-400">
                      Total:{" "}
                      <span className="font-bold">${form.numAmount.toFixed(2)}</span>
                    </p>
                  )}
                </div>
              )}
            </div>
          )}

          {!form.validation.isValid && form.validation.error && (
            <p className="text-xs text-orange-600 dark:text-orange-400 flex items-center gap-1">
              <AlertCircle size={12} />
              {form.validation.error}
            </p>
          )}

          {isError && (
            <div className="p-3 bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400 rounded-md text-sm">
              {isEditing
                ? "Failed to update expense. Please try again."
                : "Failed to create expense. Please try again."}
            </div>
          )}

          {isEditing && expense?.lastModifiedBy && (
            <p className="text-xs text-muted-foreground italic">
              Last modified by {memberNames[expense.lastModifiedBy] ?? `User ${expense.lastModifiedBy}`}
            </p>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={isPending || !form.isReadyToSubmit}
              className="flex items-center gap-2"
            >
              {isPending && <Loader2 size={16} className="animate-spin" />}
              <span>{isEditing ? "Save Changes" : "Add Expense"}</span>
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
};

export default ExpenseModal;
