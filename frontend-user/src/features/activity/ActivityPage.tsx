import { useQuery } from "@tanstack/react-query";
import { activityService } from "./activityService";
import { Card, CardContent } from "../../components/core/Card/Card";
import { Loader2, Receipt, ArrowRightLeft } from "lucide-react";

const ActivityPage = () => {
  const { data, isLoading } = useQuery({
    queryKey: ["global-activity"],
    queryFn: () => activityService.getGlobalActivity(),
  });

  const expenses = data?.expenses || [];
  const settlements = data?.settlements || [];

  const combinedActivity = [
    ...expenses.map((e) => ({ 
      ...e, 
      type: "EXPENSE" as const, 
      date: e.expenseDate || e.createdAt || "" 
    })),
    ...settlements.map((s) => ({ 
      ...s, 
      type: "SETTLEMENT" as const, 
      date: s.settledAt || s.confirmedAt || s.paidAt || s.createdAt || "" 
    })),
  ].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

  return (
    <div className="max-w-4xl mx-auto p-6">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Shared Activity</h1>
      
      {isLoading ? (
        <div className="flex justify-center p-8" data-testid="loader">
          <Loader2 className="animate-spin text-blue-600" />
        </div>
      ) : combinedActivity.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center">
            <Receipt className="mx-auto text-gray-300 mb-4" size={48} />
            <h3 className="text-lg font-medium text-gray-900 mb-1">No shared activity</h3>
            <p className="text-gray-500">Your recent expenses and settlements will appear here.</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {combinedActivity.map((activity) => (
            <Card key={`${activity.type}-${activity.id}`} className="p-4">
              <div className="flex items-center gap-4">
                <div className={`p-2 rounded-full ${activity.type === "EXPENSE" ? "bg-blue-100 text-blue-700" : "bg-purple-100 text-purple-700"}`}>
                  {activity.type === "EXPENSE" ? <Receipt size={20} /> : <ArrowRightLeft size={20} />}
                </div>
                <div>
                  <h3 className="font-semibold text-gray-900">
                    {activity.type === "EXPENSE" ? activity.description : "Settlement"}
                  </h3>
                  <p className="text-sm text-gray-500">
                    {activity.type === "EXPENSE" ? "Expense added" : "Settlement recorded"} • {new Date(activity.date).toLocaleDateString()}
                  </p>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};

export default ActivityPage;
