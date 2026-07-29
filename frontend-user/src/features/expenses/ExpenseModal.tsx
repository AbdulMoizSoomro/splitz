import React, { useState, useCallback, useMemo } from "react";
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
import type { Group } from "../../types/group";
import type {
  CreateExpenseRequest,
  UpdateExpenseRequest,
  SplitType,
  Expense,
} from "../../types/expense";
import { useAuthStore } from "../../store/authStore";
import { useDisplayNames } from "../../hooks/useDisplayName";
import { Loader2, AlertCircle } from "lucide-react";

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

  const [description, setDescription] = useState(expense?.description || "");
  const [amount, setAmount] = useState(expense?.amount?.toString() || "");
  const [paidBy, setPaidBy] = useState<number>(
    expense?.paidBy || parseInt(currentUser?.id || "0"),
  );
  const [categoryId, setCategoryId] = useState<number | undefined>(
    expense?.categoryId,
  );
  const [expenseDate, setExpenseDate] = useState(
    expense?.expenseDate?.split("T")[0] ||
      new Date().toISOString().split("T")[0],
  );
  const [selectedMembers, setSelectedMembers] = useState<number[]>(
    expense?.splits?.map((s) => s.userId) || group.members.map((m) => m.userId),
  );
  const [splitType, setSplitType] = useState<SplitType>("EQUAL");
  const [splitValues, setSplitValues] = useState<Record<number, string>>({});

  const memberIds = useMemo(() => group.members.map((m) => m.userId), [group.members]);
  const memberNames = useDisplayNames(memberIds);

  const resetForm = useCallback(() => {
    setDescription("");
    setAmount("");
    setCategoryId(undefined);
    setExpenseDate(new Date().toISOString().split("T")[0]);
    setSelectedMembers(group.members.map((m) => m.userId));
    setSplitType("EQUAL");
    setSplitValues({});
    setPaidBy(parseInt(currentUser?.id || "0"));
  }, [group.members, currentUser?.id]);

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
      resetForm();
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

  const handleMemberToggle = (userId: number) => {
    setSelectedMembers((prev) => {
      const isSelected = prev.includes(userId);
      const next = isSelected
        ? prev.filter((id) => id !== userId)
        : [...prev, userId];

      if (isSelected && splitType !== "EQUAL") {
        const nextValues = { ...splitValues };
        delete nextValues[userId];
        setSplitValues(nextValues);
      }

      return next;
    });
  };

  const handleSplitValueChange = (userId: number, value: string) => {
    setSplitValues((prev) => ({
      ...prev,
      [userId]: value,
    }));
  };

  const totalSplitValue = Object.values(splitValues).reduce(
    (sum, val) => sum + (parseFloat(val) || 0),
    0,
  );

  const numAmount = parseFloat(amount) || 0;

  const getValidationInfo = () => {
    switch (splitType) {
      case "EQUAL":
        return { isValid: true };
      case "EXACT": {
        const isExactValid = Math.abs(totalSplitValue - numAmount) < 0.01;
        return {
          isValid: isExactValid,
          message: isExactValid
            ? "Fully allocated"
            : `Remaining: $${(numAmount - totalSplitValue).toFixed(2)}`,
          error:
            !isExactValid && numAmount > 0
              ? `Total must equal $${numAmount}`
              : undefined,
        };
      }
      case "PERCENTAGE": {
        const isPercentValid = Math.abs(totalSplitValue - 100) < 0.01;
        return {
          isValid: isPercentValid,
          message: isPercentValid
            ? "100% allocated"
            : `Total: ${totalSplitValue.toFixed(1)}%`,
          error: !isPercentValid ? "Total must equal 100%" : undefined,
        };
      }
      case "SHARES": {
        const hasShares = totalSplitValue > 0;
        return {
          isValid: hasShares,
          message: `Total shares: ${totalSplitValue}`,
          error: !hasShares ? "Total shares must be greater than 0" : undefined,
        };
      }
      case "ADJUSTMENT": {
        const isAdjValid = Math.abs(totalSplitValue) < 0.01;
        return {
          isValid: isAdjValid,
          message: isAdjValid
            ? "Adjustments balanced"
            : `Offset: ${totalSplitValue > 0 ? "+" : ""}$${totalSplitValue.toFixed(2)}`,
          error: !isAdjValid ? "Adjustments must sum to $0.00" : undefined,
        };
      }
      default:
        return { isValid: true };
    }
  };

  const validation = getValidationInfo();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!amount || selectedMembers.length === 0 || !validation.isValid) return;

    const splitsPayload = selectedMembers.map((userId) => ({
      userId,
      splitType,
      splitValue:
        splitType !== "EQUAL"
          ? parseFloat(splitValues[userId] || "0")
          : undefined,
      shareAmount:
        splitType === "EXACT"
          ? parseFloat(splitValues[userId] || "0")
          : undefined,
    }));

    if (isEditing) {
      const expenseData: UpdateExpenseRequest = {
        description,
        amount: numAmount,
        paidBy,
        categoryId,
        expenseDate,
        splitType,
        splits: splitsPayload,
      };
      updateMutation.mutate(expenseData);
    } else {
      const expenseData: CreateExpenseRequest = {
        description,
        amount: numAmount,
        paidBy,
        categoryId,
        expenseDate,
        splitType,
        splits: splitsPayload,
      };
      createMutation.mutate(expenseData);
    }
  };

  const sharePerPerson =
    amount && selectedMembers.length > 0
      ? (numAmount / selectedMembers.length).toFixed(2)
      : "0.00";

  const getPlaceholder = () => {
    switch (splitType) {
      case "EXACT":
        return "0.00";
      case "PERCENTAGE":
        return "0";
      case "SHARES":
        return "1";
      case "ADJUSTMENT":
        return "0.00";
      default:
        return "";
    }
  };

  const getUnitPrefix = () =>
    splitType === "EXACT" || splitType === "ADJUSTMENT" ? "$" : "";
  const getUnitSuffix = () =>
    splitType === "PERCENTAGE" ? "%" : splitType === "SHARES" ? " shares" : "";

  const getSplitTypeLabel = (type: SplitType) => {
    switch (type) {
      case "ADJUSTMENT":
        return "Fixed Adjustment";
      default:
        return type.toLowerCase();
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
              value={description}
              onChange={(e) => setDescription(e.target.value)}
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
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0.00"
                required
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="date">Date</FieldLabel>
              <Input
                id="date"
                type="date"
                value={expenseDate}
                onChange={(e) => setExpenseDate(e.target.value)}
                required
              />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Field>
              <FieldLabel htmlFor="paidBy">Paid By</FieldLabel>
              <Select
                value={paidBy.toString()}
                onValueChange={(val) => setPaidBy(parseInt(val || "0"))}
              >
                <SelectTrigger id="paidBy" className="w-full">
                  <SelectValue placeholder="Select Payer">{paidBy === parseInt(currentUser?.id || "0") ? "You" : memberNames[paidBy] ?? `User ${paidBy}`}</SelectValue>
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
                value={categoryId?.toString() || ""}
                onValueChange={(val) => setCategoryId(val ? parseInt(val) : undefined)}
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
              value={splitType}
              onValueChange={(val) => {
                setSplitType(val as SplitType);
                setSplitValues({});
              }}
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
                    {getSplitTypeLabel(type)}
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
                        checked={selectedMembers.includes(member.userId)}
                        onCheckedChange={() => handleMemberToggle(member.userId)}
                      />
                      <label
                        htmlFor={`member-${member.userId}`}
                        className="text-sm text-foreground cursor-pointer select-none"
                      >
                        {memberNames[member.userId] ?? `User ${member.userId}`}
                      </label>
                    </div>
                    {splitType !== "EQUAL" &&
                      selectedMembers.includes(member.userId) && (
                        <div className="flex items-center gap-1 w-32">
                          <span className="text-sm text-muted-foreground">
                            {getUnitPrefix()}
                          </span>
                          <Input
                            id={`split-value-${member.userId}`}
                            type="number"
                            step={splitType === "SHARES" ? "1" : "0.01"}
                            value={splitValues[member.userId] || ""}
                            onChange={(e) =>
                              handleSplitValueChange(
                                member.userId,
                                e.target.value,
                              )
                            }
                            placeholder={getPlaceholder()}
                            aria-label={`${memberNames[member.userId] ?? `User ${member.userId}`} split value`}
                          />
                          <span className="text-xs text-muted-foreground whitespace-nowrap">
                            {getUnitSuffix()}
                          </span>
                        </div>
                      )}
                  </div>
                </div>
              ))}
            </div>
            {selectedMembers.length === 0 && (
              <p className="mt-1 text-xs text-red-500 flex items-center gap-1">
                <AlertCircle size={12} />
                Select at least one member to split with.
              </p>
            )}
          </div>

          {amount && selectedMembers.length > 0 && (
            <div
              className={`p-3 rounded-md ${validation.isValid ? "bg-blue-50 dark:bg-blue-900/20" : "bg-orange-50 dark:bg-orange-900/20"}`}
            >
              {splitType === "EQUAL" ? (
                <p className="text-sm text-blue-700 dark:text-blue-400">
                  Each person pays:{" "}
                  <span className="font-bold">${sharePerPerson}</span>
                </p>
              ) : (
                <div className="flex justify-between items-center text-sm">
                  <p
                    className={
                      validation.isValid ? "text-blue-700 dark:text-blue-400" : "text-orange-700 dark:text-orange-400"
                    }
                  >
                    {validation.message}
                  </p>
                  {splitType === "PERCENTAGE" && validation.isValid && (
                    <p className="text-blue-700 dark:text-blue-400">
                      Total:{" "}
                      <span className="font-bold">${numAmount.toFixed(2)}</span>
                    </p>
                  )}
                </div>
              )}
            </div>
          )}

          {!validation.isValid && validation.error && (
            <p className="text-xs text-orange-600 dark:text-orange-400 flex items-center gap-1">
              <AlertCircle size={12} />
              {validation.error}
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
              disabled={
                isPending ||
                !amount ||
                selectedMembers.length === 0 ||
                !validation.isValid
              }
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
