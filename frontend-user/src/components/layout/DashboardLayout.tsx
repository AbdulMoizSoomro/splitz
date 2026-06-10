import React from "react";
import { AppSidebar } from "../app-sidebar";
import { SidebarProvider, SidebarInset, SidebarTrigger } from "@/components/ui/sidebar";

import { useAuthStore } from "../../store/authStore";

interface DashboardLayoutProps {
  children: React.ReactNode;
}

const DashboardLayout = ({ children }: DashboardLayoutProps) => {
  const { user } = useAuthStore();

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <header className="flex h-16 shrink-0 items-center gap-2 border-b border-sidebar-border px-4 bg-background">
          <SidebarTrigger className="-ml-1" />
          <div className="flex-1" />
          {user && (
            <div className="text-sm font-medium text-gray-700">
              Hi, {user.username}
            </div>
          )}
        </header>
        <main className="flex flex-1 flex-col p-4 md:p-6 bg-gray-50">
          <div className="mx-auto w-full max-w-7xl">{children}</div>
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
};

export default DashboardLayout;
