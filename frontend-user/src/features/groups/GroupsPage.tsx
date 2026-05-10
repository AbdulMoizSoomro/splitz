import { useState } from "react";
import DashboardLayout from "../../components/layout/DashboardLayout";
import { Card, CardContent } from "../../components/core/Card/Card";
import Button from "../../components/core/Button/Button";
import GroupList from "./GroupList";
import CreateGroupModal from "./CreateGroupModal";
import { Plus } from "lucide-react";

const GroupsPage = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Your Groups</h1>
            <p className="text-gray-600">
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
        </div>

        <Card>
          <CardContent className="pt-6">
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
};

export default GroupsPage;
