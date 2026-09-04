import { useState } from "react";
import DashboardLayout from "../../components/layout/DashboardLayout";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Plus } from "lucide-react";
import GroupList from "../groups/GroupList";
import CreateGroupModal from "../groups/CreateGroupModal";

function Dashboard() {
  const [isModalOpen, setIsModalOpen] = useState(false);

  return (
    <DashboardLayout breadcrumbs={[{ label: "Dashboard" }]}>
      <div className="space-y-8">
        {/* Welcome Card */}
        <Card className="border-border bg-card">
          <CardHeader className="flex flex-row justify-between items-center">
            <div>
              <CardTitle className="text-2xl font-bold">Welcome to Splitz</CardTitle>
              <p className="text-muted-foreground mt-1">
                Track shared expenses, manage groups, and settle balances easily.
              </p>
            </div>
            <Button
              onClick={() => setIsModalOpen(true)}
              className="flex items-center gap-2"
            >
              <Plus size={18} />
              <span>Create Group</span>
            </Button>
          </CardHeader>
        </Card>

        {/* Your Groups */}
        <Card className="border-border bg-card">
          <CardHeader>
            <CardTitle className="text-lg font-semibold">Your Groups</CardTitle>
          </CardHeader>
          <CardContent>
            <GroupList />
          </CardContent>
        </Card>
      </div>

      <CreateGroupModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
      />
    </DashboardLayout>
  );
}

export default Dashboard;
