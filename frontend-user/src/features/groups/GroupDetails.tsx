import DashboardLayout from "../../components/layout/DashboardLayout";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Loader2, Receipt, Users, DollarSign, Activity } from "lucide-react";
import GroupBalances from "../balances/GroupBalances";
import GroupActivity from "./GroupActivity";
import { useGroupDetailsSession, type TabType } from "./useGroupDetailsSession";
import { GroupHeader } from "./components/GroupHeader";
import { GroupBalanceBanner } from "./components/GroupBalanceBanner";
import { GroupExpensesTab } from "./components/GroupExpensesTab";
import { GroupMembersTab } from "./components/GroupMembersTab";
import { GroupModals } from "./components/GroupModals";

const GroupDetails = () => {
  const session = useGroupDetailsSession();
  const {
    navigate,
    group,
    isLoading,
    isBalancesLoading,
    currentUserBalance,
    user,
    expenses,
    sortedExpenses,
    isExpensesLoading,
    categories,
    memberNames,
    friends,
    balancesResponse,
    governance,
    activeTab,
    setActiveTab,
    handleAddExpense,
    handleEditExpense,
    handleDeleteExpenseClick,
    handleRoleUpdate,
    updateRoleMutation,
    updateGroupMutation,
    setIsAddMemberModalOpen,
    setIsLeaveModalOpen,
  } = session;

  if (isLoading) {
    return (
      <DashboardLayout>
        <div className="flex justify-center py-12">
          <Loader2 className="animate-spin text-blue-600" size={48} />
        </div>
      </DashboardLayout>
    );
  }

  if (!group) {
    return (
      <DashboardLayout>
        <div className="text-center py-12">
          <h2 className="text-xl font-bold text-foreground">Group not found</h2>
          <Button onClick={() => navigate("/groups")} className="mt-4">
            Back to Groups
          </Button>
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout
      breadcrumbs={[
        { label: "Groups", href: "/groups" },
        { label: group.name },
      ]}
    >
      {/* Signals that this route's lazy chunk has loaded and its content is interactive. */}
      <div data-testid="group-details" hidden />
      <div className="flex flex-col h-[calc(100vh-112px)] overflow-hidden space-y-4 pb-2">
        <GroupHeader group={group} />

        <Tabs
          value={activeTab}
          onValueChange={(val) => setActiveTab(val as TabType)}
          className="w-full flex-1 flex flex-col min-h-0"
        >
          <TabsList
            variant="line"
            className="border-b border-border shrink-0 w-full justify-start gap-8 bg-transparent p-0 rounded-none h-auto -mb-px"
          >
            <TabsTrigger
              value="expenses"
              className="py-4 px-1 border-b-2 bg-transparent rounded-none border-transparent text-muted-foreground hover:text-foreground hover:border-gray-300 data-[active]:border-blue-500 data-[active]:text-blue-600"
            >
              <div className="flex items-center gap-2">
                <Receipt size={18} />
                <span>Expenses</span>
              </div>
            </TabsTrigger>
            <TabsTrigger
              value="members"
              className="py-4 px-1 border-b-2 bg-transparent rounded-none border-transparent text-muted-foreground hover:text-foreground hover:border-gray-300 data-[active]:border-blue-500 data-[active]:text-blue-600"
            >
              <div className="flex items-center gap-2">
                <Users size={18} />
                <span>Members</span>
              </div>
            </TabsTrigger>
            <TabsTrigger
              value="balances"
              className="py-4 px-1 border-b-2 bg-transparent rounded-none border-transparent text-muted-foreground hover:text-foreground hover:border-gray-300 data-[active]:border-blue-500 data-[active]:text-blue-600"
            >
              <div className="flex items-center gap-2">
                <DollarSign size={18} />
                <span>Balances</span>
              </div>
            </TabsTrigger>
          </TabsList>

          <GroupBalanceBanner
            isBalancesLoading={isBalancesLoading}
            currentUserBalance={currentUserBalance}
            onViewDetails={(tab) => setActiveTab(tab)}
          />

          <div className="grid grid-cols-1 lg:grid-cols-4 gap-6 flex-1 min-h-0 pt-4">
            <div className="lg:col-span-3 h-full flex flex-col min-h-0">
              <GroupExpensesTab
                expenses={expenses}
                sortedExpenses={sortedExpenses}
                isExpensesLoading={isExpensesLoading}
                categories={categories}
                memberNames={memberNames}
                userId={Number(user?.id)}
                governance={governance}
                onAddExpense={handleAddExpense}
                onEditExpense={handleEditExpense}
                onDeleteExpense={handleDeleteExpenseClick}
              />

              <GroupMembersTab
                group={group}
                userId={Number(user?.id)}
                memberNames={memberNames}
                friends={friends}
                balancesResponse={balancesResponse}
                isBalancesLoading={isBalancesLoading}
                governance={governance}
                onOpenAddMemberModal={() => setIsAddMemberModalOpen(true)}
                onRoleUpdate={handleRoleUpdate}
                onOpenLeaveModal={() => setIsLeaveModalOpen(true)}
                updateRoleMutationPending={updateRoleMutation.isPending}
                updateGroupMutation={updateGroupMutation}
              />

              <TabsContent
                value="balances"
                className="h-full overflow-y-auto pr-1"
              >
                <GroupBalances groupId={session.groupId} />
              </TabsContent>
            </div>

            <div className="lg:col-span-1 h-full flex flex-col min-h-0 space-y-4">
              <div className="px-1 flex items-center justify-between shrink-0">
                <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                  <Activity size={20} className="text-blue-600" />
                  <span>Shared Activity</span>
                </h2>
              </div>
              <div className="flex-1 overflow-y-auto pr-1">
                <GroupActivity
                  groupId={session.groupId}
                  onEditExpense={handleEditExpense}
                  group={group}
                />
              </div>
            </div>
          </div>
        </Tabs>
      </div>

      <GroupModals session={session} />
    </DashboardLayout>
  );
};

export default GroupDetails;
