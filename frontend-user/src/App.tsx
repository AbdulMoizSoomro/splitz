import { lazy, Suspense } from "react";
import { Routes, Route } from "react-router-dom";
import { Loader2 } from "lucide-react";
import Login from "./features/auth/Login";
import Register from "./features/auth/Register";
import ProtectedRoute from "./components/auth/ProtectedRoute";
import { Toaster } from "@/components/ui/sonner";

/**
 * Authenticated pages are loaded on demand so the entry bundle carries only what an
 * unauthenticated visitor needs. Each becomes its own chunk, which also keeps shared page-specific
 * dependencies (the friends ledger, the expense form engine) out of the initial payload.
 *
 * Login and Register are imported eagerly on purpose: they are the first paint for most visitors,
 * and deferring them would trade a smaller bundle for a visible loading flash before a form.
 */
const Dashboard = lazy(() => import("./features/dashboard/Dashboard"));
const GroupsPage = lazy(() => import("./features/groups/GroupsPage"));
const GroupDetails = lazy(() => import("./features/groups/GroupDetails"));
const FriendsPage = lazy(() => import("./features/users/FriendsPage"));
const FriendDetailPage = lazy(() => import("./features/users/FriendDetailPage"));
const ActivityPage = lazy(() => import("./features/activity/ActivityPage"));
const SettingsPage = lazy(() => import("./features/settings/SettingsPage"));

function RouteFallback() {
  return (
    <div
      className="flex flex-col items-center justify-center min-h-screen gap-3"
      data-testid="route-loader"
      role="status"
      aria-live="polite"
    >
      <Loader2 className="animate-spin text-blue-600" size={32} />
      <p className="text-sm text-muted-foreground">Loading...</p>
    </div>
  );
}

function App() {
  return (
    <>
      <Suspense fallback={<RouteFallback />}>
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
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Routes>
      </Suspense>
      <Toaster />
    </>
  );
}

export default App;