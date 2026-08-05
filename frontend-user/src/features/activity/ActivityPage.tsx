import { useNavigate } from "react-router-dom";
import DashboardLayout from "../../components/layout/DashboardLayout";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Loader2, Receipt } from "lucide-react";
import { useActivityStreamEngine, ACTIVITY_FILTERS, activityFilterLabel } from "./activityStreamEngine";
import { ActivityCard } from "./components/ActivityCard";

const ActivityPage = () => {
  const navigate = useNavigate();
  const { groupedActivity, filter, setFilter, isLoading } =
    useActivityStreamEngine();

  const isEmpty = groupedActivity.every((bucket) => bucket.items.length === 0);

  return (
    <DashboardLayout breadcrumbs={[{ label: "Activity" }]}>
      <div className="max-w-5xl mx-auto p-4 md:p-6 space-y-6">
        {/* Header Block */}
        <Card className="relative overflow-hidden bg-gradient-to-r from-slate-900 via-slate-800 to-indigo-950 text-white border-0 shadow-xl">
          <div className="absolute right-0 top-0 -mr-16 -mt-16 h-48 w-48 rounded-full bg-indigo-500 opacity-20 blur-3xl"></div>
          <CardHeader className="relative z-10 pb-4">
            <CardTitle className="text-3xl font-extrabold tracking-tight">Shared Activity</CardTitle>
            <CardDescription className="text-slate-300 text-sm max-w-xl">
              Keep track of all your group expenses, personal settlements, and payment transactions across your entire network.
            </CardDescription>
          </CardHeader>

          <CardContent className="relative z-10">
            {/* Filter Pills */}
            <div className="flex gap-2">
              {ACTIVITY_FILTERS.map((t) => (
                <button
                  key={t}
                  onClick={() => setFilter(t)}
                  className={`px-4 py-2 rounded-full text-xs font-semibold tracking-wide uppercase transition-all duration-300 ${filter === t
                    ? "bg-white text-slate-900 shadow-md transform scale-105"
                    : "bg-slate-800/80 text-slate-300 hover:bg-slate-700/80 hover:text-white"
                    }`}
                >
                  {activityFilterLabel(t)}
                </button>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Content Section */}
        {isLoading ? (
          <div className="flex flex-col items-center justify-center p-24 space-y-4" data-testid="loader">
            <Loader2 className="animate-spin text-indigo-600" size={40} />
            <p className="text-sm font-medium text-slate-500 animate-pulse">Loading all activities...</p>
          </div>
        ) : isEmpty ? (
          <Card className="border-dashed border-2 border-slate-200">
            <CardContent className="py-16 text-center">
              <div className="mx-auto w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mb-4">
                <Receipt className="text-slate-400" size={32} />
              </div>
              <CardTitle className="text-xl font-bold mb-1">No activity found</CardTitle>
              <CardDescription className="max-w-sm mx-auto text-sm">
                No recent records match your filter. Create a group or add an expense to see details here!
              </CardDescription>
            </CardContent>
          </Card>
        ) : (
          <ScrollArea className="h-[550px] pr-4">
            <div className="space-y-6">
              {groupedActivity.map((bucket) => (
                <div key={bucket.label} className="space-y-2">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-muted-foreground px-1">
                    {bucket.label}
                  </h3>
                  <div className="space-y-4">
                    {bucket.items.map((view) => (
                      <ActivityCard
                        key={view.key}
                        view={view}
                        onGroupClick={(groupId) => navigate(`/groups/${groupId}`)}
                      />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </ScrollArea>
        )}
      </div>
    </DashboardLayout>
  );
};

export default ActivityPage;