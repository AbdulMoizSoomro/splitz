import { Routes, Route } from 'react-router-dom';
import Login from './features/auth/Login';
import Register from './features/auth/Register';
import ProtectedRoute from './components/auth/ProtectedRoute';
import Dashboard from './features/dashboard/Dashboard';
import GroupsPage from './features/groups/GroupsPage';
import FriendsPage from './features/users/FriendsPage';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/friends" element={<FriendsPage />} />
        <Route path="/groups" element={<GroupsPage />} />
      </Route>
    </Routes>
  );
}

export default App;
