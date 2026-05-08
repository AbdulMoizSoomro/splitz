import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Loader2, Folder, Users, ReceiptText } from 'lucide-react';
import { groupService } from './groupService';
import Button from '../../components/core/Button/Button';
import CreateExpenseModal from '../expenses/CreateExpenseModal';
import type { Group } from '../../types/group';

const GroupList = () => {
  const [selectedGroup, setSelectedGroup] = useState<Group | null>(null);
  const [isExpenseModalOpen, setIsExpenseModalOpen] = useState(false);
  const navigate = useNavigate();

  const { data: groups, isLoading } = useQuery({
    queryKey: ['groups'],
    queryFn: groupService.getGroups,
  });

  if (isLoading) {
    return (
      <div className="flex justify-center py-8" data-testid="loader">
        <Loader2 className="animate-spin text-blue-600" size={32} />
      </div>
    );
  }

  if (!groups || groups.length === 0) {
    return (
      <div className="text-center py-12 bg-white border border-dashed border-gray-300 rounded-xl">
        <Folder className="mx-auto text-gray-400 mb-4" size={48} />
        <h3 className="text-lg font-medium text-gray-900">No groups found</h3>
        <p className="text-gray-500 mt-1">Create a group to start splitting expenses with friends.</p>
      </div>
    );
  }

  const handleAddExpense = (group: Group, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedGroup(group);
    setIsExpenseModalOpen(true);
  };

  return (
    <>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {groups.map((group) => (
          <div
            key={group.id}
            onClick={() => navigate(`/groups/${group.id}`)}
            className="p-5 bg-white border border-gray-200 rounded-xl shadow-sm hover:shadow-md transition-shadow cursor-pointer flex flex-col h-full"
          >
            <div className="flex justify-between items-start mb-3">
              <div className="p-2 bg-blue-50 rounded-lg">
                <Folder className="text-blue-600" size={24} />
              </div>
              <div className="flex items-center text-gray-500 text-sm">
                <Users size={16} className="mr-1" />
                <span>{group.members?.length || 0}</span>
              </div>
            </div>
            <h3 className="text-lg font-bold text-gray-900 mb-1">{group.name}</h3>
            {group.description && (
              <p className="text-sm text-gray-600 line-clamp-2 flex-grow">{group.description}</p>
            )}
            <div className="mt-4 pt-4 border-t border-gray-100 flex justify-end">
              <Button
                variant="ghost"
                size="sm"
                className="text-xs flex items-center gap-1.5"
                onClick={(e) => handleAddExpense(group, e)}
              >
                <ReceiptText size={14} />
                <span>Add Expense</span>
              </Button>
            </div>
          </div>
        ))}
      </div>

      {selectedGroup && (
        <CreateExpenseModal
          isOpen={isExpenseModalOpen}
          onClose={() => {
            setIsExpenseModalOpen(false);
            setSelectedGroup(null);
          }}
          group={selectedGroup}
        />
      )}
    </>
  );
};

export default GroupList;
