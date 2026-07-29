import { useState } from "react";
import DashboardLayout from "../../components/layout/DashboardLayout";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import GroupList from "./GroupList";
import CreateGroupModal from "./CreateGroupModal";
import { Plus } from "lucide-react";

const GroupsPage = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);

  return (
    <DashboardLayout breadcrumbs={[{ label: "Groups" }]}>
      <Card className="border-border bg-card">
        <CardHeader className="flex flex-row justify-between items-center">
          <div>
            <h1 className="text-2xl font-bold text-foreground">Your Groups</h1>
            <p className="text-muted-foreground">
              Manage your expense groups and members.
            </p>
          </div>
          <Button
            onClick={() => setIsModalOpen(true)}
            className="flex items-center gap-2"
          >
            <Plus size={20} />
            <span>Create Group</span>
          </Button>
        </CardHeader>
        <CardContent>
          <GroupList />
        </CardContent>
      </Card>

      <CreateGroupModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
      />
    </DashboardLayout>
  );
};

export default GroupsPage;
