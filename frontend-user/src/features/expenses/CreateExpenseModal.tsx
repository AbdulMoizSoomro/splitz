import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Modal from '../../components/core/Modal/Modal';
import Input from '../../components/core/Input/Input';
import Button from '../../components/core/Button/Button';
import { expenseService } from './expenseService';
import { categoryService } from './categoryService';
import type { Group } from '../../types/group';
import type { CreateExpenseRequest } from '../../types/expense';
import { useAuthStore } from '../../store/authStore';
import { Loader2, AlertCircle } from 'lucide-react';

interface CreateExpenseModalProps {
  isOpen: boolean;
  onClose: () => void;
  group: Group;
}

const CreateExpenseModal = ({ isOpen, onClose, group }: CreateExpenseModalProps) => {
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined);
  const [expenseDate, setExpenseDate] = useState(new Date().toISOString().split('T')[0]);
  const [selectedMembers, setSelectedMembers] = useState<number[]>(
    group.members.map((m) => m.userId)
  );
  const [splitType, setSplitType] = useState<'EQUAL' | 'EXACT'>('EQUAL');
  const [exactShares, setExactShares] = useState<Record<number, string>>({});

  const currentUser = useAuthStore((state) => state.user);
  const queryClient = useQueryClient();

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: categoryService.getCategories,
    enabled: isOpen,
  });

  const createMutation = useMutation({
    mutationFn: (data: CreateExpenseRequest) => expenseService.createExpense(group.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['expenses', group.id] });
      onClose();
      resetForm();
    },
  });

  const resetForm = () => {
    setDescription('');
    setAmount('');
    setCategoryId(undefined);
    setExpenseDate(new Date().toISOString().split('T')[0]);
    setSelectedMembers(group.members.map((m) => m.userId));
    setSplitType('EQUAL');
    setExactShares({});
  };

  const handleMemberToggle = (userId: number) => {
    setSelectedMembers((prev) => {
      const next = prev.includes(userId) ? prev.filter((id) => id !== userId) : [...prev, userId];
      
      // If we are in EXACT mode and unselecting a member, remove their share
      if (splitType === 'EXACT' && prev.includes(userId)) {
        const nextShares = { ...exactShares };
        delete nextShares[userId];
        setExactShares(nextShares);
      }
      
      return next;
    });
  };

  const handleExactShareChange = (userId: number, value: string) => {
    setExactShares((prev) => ({
      ...prev,
      [userId]: value,
    }));
  };

  const totalExactAllocated = Object.values(exactShares).reduce(
    (sum, val) => sum + (parseFloat(val) || 0),
    0
  );

  const isExactSplitValid =
    splitType === 'EQUAL' ||
    (amount && Math.abs(totalExactAllocated - parseFloat(amount)) < 0.01);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!amount || selectedMembers.length === 0 || !isExactSplitValid) return;

    const expenseData = {
      description,
      amount: parseFloat(amount),
      paidBy: parseInt(currentUser?.id || '0'),
      categoryId,
      expenseDate,
      splitType,
      splits: selectedMembers.map((userId) => ({
        userId,
        splitType,
        splitValue: splitType === 'EXACT' ? parseFloat(exactShares[userId] || '0') : undefined,
        shareAmount: splitType === 'EXACT' ? parseFloat(exactShares[userId] || '0') : undefined,
      })),
    };

    createMutation.mutate(expenseData);
  };

  const sharePerPerson =
    amount && selectedMembers.length > 0
      ? (parseFloat(amount) / selectedMembers.length).toFixed(2)
      : '0.00';

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Add New Expense">
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="Description"
          id="description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="e.g., Dinner, Groceries"
          required
        />

        <div className="grid grid-cols-2 gap-4">
          <Input
            label="Amount"
            id="amount"
            type="number"
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="0.00"
            required
          />
          <Input
            label="Date"
            id="date"
            type="date"
            value={expenseDate}
            onChange={(e) => setExpenseDate(e.target.value)}
            required
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
          <select
            value={categoryId || ''}
            onChange={(e) => setCategoryId(e.target.value ? parseInt(e.target.value) : undefined)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
          >
            <option value="">Select a category</option>
            {categories?.map((cat) => (
              <option key={cat.id} value={cat.id}>
                {cat.name}
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center gap-4 py-2 border-y border-gray-100">
          <span className="text-sm font-medium text-gray-700">Split Type:</span>
          <label className="flex items-center gap-1.5 cursor-pointer">
            <input
              type="radio"
              name="splitType"
              value="EQUAL"
              checked={splitType === 'EQUAL'}
              onChange={() => setSplitType('EQUAL')}
              className="w-4 h-4 text-blue-600"
            />
            <span className="text-sm text-gray-700">Equal</span>
          </label>
          <label className="flex items-center gap-1.5 cursor-pointer">
            <input
              type="radio"
              name="splitType"
              value="EXACT"
              checked={splitType === 'EXACT'}
              onChange={() => setSplitType('EXACT')}
              className="w-4 h-4 text-blue-600"
            />
            <span className="text-sm text-gray-700">Exact</span>
          </label>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            Split between members
          </label>
          <div className="max-h-60 overflow-y-auto border border-gray-200 rounded-md p-2 space-y-3">
            {group.members.map((member) => (
              <div key={member.userId} className="flex flex-col gap-2 p-2 rounded-md hover:bg-gray-50">
                <div className="flex items-center justify-between">
                  <div className="flex items-center">
                    <input
                      type="checkbox"
                      id={`member-${member.userId}`}
                      checked={selectedMembers.includes(member.userId)}
                      onChange={() => handleMemberToggle(member.userId)}
                      className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
                    />
                    <label htmlFor={`member-${member.userId}`} className="ml-2 text-sm text-gray-900">
                      User {member.userId} {member.userId === parseInt(currentUser?.id || '0') && '(You)'}
                    </label>
                  </div>
                  {splitType === 'EXACT' && selectedMembers.includes(member.userId) && (
                    <div className="w-24">
                      <Input
                        id={`share-${member.userId}`}
                        type="number"
                        step="0.01"
                        value={exactShares[member.userId] || ''}
                        onChange={(e) => handleExactShareChange(member.userId, e.target.value)}
                        placeholder="0.00"
                        inputSize="sm"
                        aria-label={`User ${member.userId} share`}
                      />
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
          <div className={`p-3 rounded-md ${isExactSplitValid ? 'bg-blue-50' : 'bg-orange-50'}`}>
            {splitType === 'EQUAL' ? (
              <p className="text-sm text-blue-700">
                Each person pays: <span className="font-bold">${sharePerPerson}</span>
              </p>
            ) : (
              <div className="flex justify-between items-center text-sm">
                <p className={isExactSplitValid ? 'text-blue-700' : 'text-orange-700'}>
                  Total split: <span className="font-bold">${totalExactAllocated.toFixed(2)}</span>
                </p>
                <p className={isExactSplitValid ? 'text-blue-700' : 'text-orange-700 font-bold'}>
                  {Math.abs(totalExactAllocated - parseFloat(amount)) < 0.01
                    ? 'Fully allocated'
                    : `Remaining: $${(parseFloat(amount) - totalExactAllocated).toFixed(2)}`}
                </p>
              </div>
            )}
          </div>
        )}

        {splitType === 'EXACT' && amount && !isExactSplitValid && (
          <p className="text-xs text-orange-600 flex items-center gap-1">
            <AlertCircle size={12} />
            Total split must equal {amount}
          </p>
        )}

        {createMutation.isError && (
          <div className="p-3 bg-red-50 text-red-700 rounded-md text-sm">
            Failed to create expense. Please try again.
          </div>
        )}

        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            disabled={createMutation.isPending || !amount || selectedMembers.length === 0 || !isExactSplitValid}
            className="flex items-center gap-2"
          >
            {createMutation.isPending && <Loader2 size={16} className="animate-spin" />}
            <span>Add Expense</span>
          </Button>
        </div>
      </form>
    </Modal>
  );
};

export default CreateExpenseModal;
