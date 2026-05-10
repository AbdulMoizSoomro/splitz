import DashboardLayout from "../../components/layout/DashboardLayout";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "../../components/core/Card/Card";
import UserSearch from "./UserSearch";
import FriendRequestsList from "./FriendRequestsList";
import FriendsList from "./FriendsList";
import TempFriendsList from "./TempFriendsList";

const FriendsPage = () => {
  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Friends</h1>
          <p className="text-gray-600">
            Manage your connections and find new friends.
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Main Content */}
          <div className="lg:col-span-2 space-y-8">
            <Card>
              <CardHeader>
                <CardTitle>Find Friends</CardTitle>
              </CardHeader>
              <CardContent>
                <UserSearch />
              </CardContent>
            </Card>

            <TempFriendsList />

            <Card>
              <CardHeader>
                <CardTitle>Your Friends</CardTitle>
              </CardHeader>
              <CardContent>
                <FriendsList />
              </CardContent>
            </Card>
          </div>

          {/* Sidebar */}
          <div className="space-y-8">
            <Card>
              <CardHeader>
                <CardTitle>Friend Requests</CardTitle>
              </CardHeader>
              <CardContent>
                <FriendRequestsList />
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
};

export default FriendsPage;
