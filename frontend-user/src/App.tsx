import { Routes, Route } from "react-router-dom";
import Login from "./features/auth/Login";
import Register from "./features/auth/Register";
import ProtectedRoute from "./components/auth/ProtectedRoute";
import Dashboard from "./features/dashboard/Dashboard";
import GroupsPage from "./features/groups/GroupsPage";
import GroupDetails from "./features/groups/GroupDetails";
import FriendsPage from "./features/users/FriendsPage";
import FriendDetailPage from "./features/users/FriendDetailPage";
import ActivityPage from "./features/activity/ActivityPage";
import ToastContainer from "./components/core/Toast/ToastContainer";

function App() {
  return (
    <>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/friends" element={<FriendsPage />} />
          <Route path="/friends/:id" element={<FriendDetailPage />} />
          <Route path="/groups" element={<GroupsPage />} />
          <Route path="/groups/:id" element={<GroupDetails />} />
          <Route path="/activity" element={<ActivityPage />} />
        </Route>
      </Routes>
      <ToastContainer />
    </>
  );
}

export default App;
