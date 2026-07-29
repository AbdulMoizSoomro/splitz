import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { Loader2, Folder, Users, ReceiptText } from "lucide-react";
import { groupService } from "./groupService";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import ExpenseModal from "../expenses/ExpenseModal";
import type { Group } from "../../types/group";
import { useAuthStore } from "../../store/authStore";

const GroupList = () => {
  const [selectedGroup, setSelectedGroup] = useState<Group | null>(null);
  const [isExpenseModalOpen, setIsExpenseModalOpen] = useState(false);
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const currentUserId = Number(user?.id);

  const { data: groups, isLoading } = useQuery({
    queryKey: ["groups"],
    queryFn: groupService.getGroups,
  });

  const { data: userBalancesData, isLoading: isBalancesLoading } = useQuery({
    queryKey: ["user-balances", currentUserId],
    queryFn: () => groupService.getUserBalances(currentUserId),
    enabled: !!currentUserId,
  });

  const totalGroupBalance = userBalancesData?.groupBalances.reduce(
    (sum, gb) => sum + gb.balance,
    0,
  ) ?? 0;

  if (isLoading) {
    return (
      <div className="flex justify-center py-8" data-testid="loader">
        <Loader2 className="animate-spin text-blue-600" size={32} />
      </div>
    );
  }

  if (!groups || groups.length === 0) {
    return (
      <Card className="text-center py-12 bg-muted/50 border border-dashed border-border shadow-none">
        <CardContent>
          <Folder className="mx-auto text-muted-foreground mb-4" size={48} />
          <h3 className="text-lg font-medium text-foreground">No groups found</h3>
          <p className="text-muted-foreground mt-1">
            Create a group to start splitting expenses with friends.
          </p>
        </CardContent>
      </Card>
    );
  }

  const handleAddExpense = (group: Group, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedGroup(group);
    setIsExpenseModalOpen(true);
  };

  return (
    <>
      {/* Overall Group Balance Highlight Info */}
      {!isBalancesLoading && userBalancesData && (
        <Card className="mb-6 shadow-sm border-border bg-muted/50">
          <CardContent className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className={`p-2 rounded-full ${
                totalGroupBalance > 0 
                  ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300" 
                  : totalGroupBalance < 0 
                  ? "bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-300" 
                  : "bg-muted text-muted-foreground"
              }`}>
                <Folder size={20} />
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">Total Group Balance</p>
                <h3 className={`text-lg font-bold ${
                  totalGroupBalance > 0 
                    ? "text-emerald-600" 
                    : totalGroupBalance < 0 
                    ? "text-rose-600" 
                    : "text-muted-foreground"
                }`}>
                  {totalGroupBalance > 0 
                    ? `You are owed $${totalGroupBalance.toFixed(2)} across groups` 
                    : totalGroupBalance < 0 
                    ? `You owe $${Math.abs(totalGroupBalance).toFixed(2)} across groups` 
                    : "You are all settled up in your groups!"
                  }
                </h3>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {groups.map((group) => {
          const groupBal = userBalancesData?.groupBalances.find((gb) => gb.groupId === group.id)?.balance ?? 0;
          return (
            <Card
              key={group.id}
              onClick={() => navigate(`/groups/${group.id}`)}
              className="cursor-pointer hover:shadow-md transition-shadow flex flex-col h-full border-border"
            >
              <CardContent className="p-5 flex flex-col flex-grow">
                <div className="flex justify-between items-start mb-3">
                  <div className="p-2 bg-blue-50 dark:bg-blue-900/30 rounded-lg">
                    <Folder className="text-blue-600 dark:text-blue-400" size={24} />
                  </div>
                  <div className="flex items-center text-muted-foreground text-sm">
                    <Users size={16} className="mr-1" />
                    <span>{group.members?.length || 0}</span>
                  </div>
                </div>
                <h3 className="text-lg font-bold text-foreground mb-1">
                  {group.name}
                </h3>
                {group.description && (
                  <p className="text-sm text-muted-foreground line-clamp-2 flex-grow">
                    {group.description}
                  </p>
                )}
                <div className="mt-4 pt-4 border-t border-border flex justify-between items-center">
                  <div className="text-xs font-semibold">
                    {isBalancesLoading ? (
                      <span className="text-muted-foreground animate-pulse">Loading balance...</span>
                    ) : groupBal > 0 ? (
                      <span className="text-emerald-600">You are owed ${groupBal.toFixed(2)}</span>
                    ) : groupBal < 0 ? (
                      <span className="text-rose-600">You owe ${Math.abs(groupBal).toFixed(2)}</span>
                    ) : (
                      <span className="text-muted-foreground font-normal">Settled up</span>
                    )}
                  </div>
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
              </CardContent>
            </Card>
          );
        })}
      </div>

      {selectedGroup && isExpenseModalOpen && (
        <ExpenseModal
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
