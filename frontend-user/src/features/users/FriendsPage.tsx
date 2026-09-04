import DashboardLayout from "../../components/layout/DashboardLayout";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import UserSearch from "./UserSearch";
import FriendRequestsList from "./FriendRequestsList";
import FriendsList from "./FriendsList";

const FriendsPage = () => {
  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Friends & Balances</h1>
          <p className="text-muted-foreground">
            Manage your connections, track who owes what, and settle balances instantly.
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Main Content: Friends & Balances */}
          <div className="lg:col-span-2 space-y-6">
            <FriendsList />
          </div>

          {/* Sidebar: Find Friends & Friend Requests */}
          <div className="space-y-6">
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-base font-semibold">Find Friends</CardTitle>
              </CardHeader>
              <CardContent>
                <UserSearch />
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-base font-semibold">Friend Requests</CardTitle>
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
