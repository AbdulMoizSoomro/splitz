import { useState } from "react";
import DashboardLayout from "../../components/layout/DashboardLayout";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "../../components/core/Card/Card";
import Button from "../../components/core/Button/Button";
import UserSearch from "../users/UserSearch";
import FriendRequestsList from "../users/FriendRequestsList";
import FriendsList from "../users/FriendsList";
import CreateGroupModal from "../groups/CreateGroupModal";

function Dashboard() {
  const [isModalOpen, setIsModalOpen] = useState(false);

  return (
    <DashboardLayout>
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Main Content */}
        <div className="lg:col-span-2 space-y-8">
          <Card>
            <CardHeader>
              <CardTitle>Welcome to Splitz</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-gray-600 mb-4">
                Start by adding your friends or creating a group to split
                expenses.
              </p>
              <div className="flex gap-2">
                <Button onClick={() => setIsModalOpen(true)}>
                  Create Group
                </Button>
              </div>
            </CardContent>
          </Card>

          <div className="max-w-2xl">
            <h2 className="text-xl font-bold mb-4 text-gray-900">
              Find Friends
            </h2>
            <UserSearch />
          </div>
        </div>

        {/* Sidebar */}
        <div className="space-y-8">
          <div>
            <h2 className="text-xl font-bold mb-4 text-gray-900">
              Friend Requests
            </h2>
            <FriendRequestsList />
          </div>

          <div>
            <h2 className="text-xl font-bold mb-4 text-gray-900">
              Your Friends
            </h2>
            <FriendsList />
          </div>
        </div>
      </div>

      <CreateGroupModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
      />
    </DashboardLayout>
  );
}

export default Dashboard;
