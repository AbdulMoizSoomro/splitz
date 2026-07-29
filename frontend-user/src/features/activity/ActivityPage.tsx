import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { activityService } from "./activityService";
import { groupService } from "../groups/groupService";
import { useAuthStore } from "../../store/authStore";
import { useDisplayNames } from "../../hooks/useDisplayName";
import { useNavigate } from "react-router-dom";
import DashboardLayout from "../../components/layout/DashboardLayout";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Loader2,
  Receipt,
  ArrowRightLeft,
  Calendar,
  Clock,
  ArrowUpRight,
  ArrowDownLeft,
  Info,
  Globe
} from "lucide-react";

const ActivityPage = () => {
  const { user } = useAuthStore();
  const currentUserId = Number(user?.id);
  const navigate = useNavigate();
  const [filter, setFilter] = useState<"ALL" | "EXPENSE" | "SETTLEMENT">("ALL");

  // 1. Fetch Global Activity
  const { data, isLoading } = useQuery({
    queryKey: ["global-activity"],
    queryFn: () => activityService.getGlobalActivity(),
  });

  const expenses = data?.expenses || [];
  const settlements = data?.settlements || [];

  // Fetch groups to map groupIds to group names
  const { data: groups } = useQuery({
    queryKey: ["groups"],
    queryFn: () => groupService.getGroups(),
  });

  // Collect all unique user IDs present in the activity feed
  const uniqueUserIds = useMemo(() => {
    const ids = new Set<number>();
    expenses.forEach((e) => ids.add(e.paidBy));
    settlements.forEach((s) => {
      ids.add(s.payerId);
      ids.add(s.payeeId);
    });
    return [...ids];
  }, [expenses, settlements]);

  // Resolve user IDs → display names (self → "You", friends/members → "First Last")
  const nameMap = useDisplayNames(uniqueUserIds);

  // Helper: Get group name from ID
  const getGroupName = (groupId: number) => {
    const group = groups?.find((g) => g.id === groupId);
    return group ? group.name : `Group #${groupId}`;
  };

  // Process activities
  const combinedActivity = [
    ...expenses.map((e) => ({
      ...e,
      type: "EXPENSE" as const,
      date: e.expenseDate || e.createdAt || "",
    })),
    ...settlements.map((s) => ({
      ...s,
      type: "SETTLEMENT" as const,
      date: s.settledAt || s.confirmedAt || s.paidAt || s.createdAt || "",
    })),
  ]
    .filter((activity) => filter === "ALL" || activity.type === filter)
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

  // Date and Time formatter
  const formatDateTime = (dateStr: string) => {
    if (!dateStr) return { date: "Unknown Date", time: "" };
    const d = new Date(dateStr);
    const date = d.toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
    const time = d.toLocaleTimeString("en-US", {
      hour: "2-digit",
      minute: "2-digit",
    });
    return { date, time };
  };

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
              {(["ALL", "EXPENSE", "SETTLEMENT"] as const).map((t) => (
                <button
                  key={t}
                  onClick={() => setFilter(t)}
                  className={`px-4 py-2 rounded-full text-xs font-semibold tracking-wide uppercase transition-all duration-300 ${filter === t
                    ? "bg-white text-slate-900 shadow-md transform scale-105"
                    : "bg-slate-800/80 text-slate-300 hover:bg-slate-700/80 hover:text-white"
                    }`}
                >
                  {t === "ALL" ? "All Activity" : t === "EXPENSE" ? "Expenses" : "Settlements & Payments"}
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
        ) : combinedActivity.length === 0 ? (
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
            <div className="space-y-4">
              {combinedActivity.map((activity) => {
                const { date, time } = formatDateTime(activity.date);

                // ------------------ EXPENSE TYPE RENDERING ------------------
                if (activity.type === "EXPENSE") {
                  const isPayer = activity.paidBy === currentUserId;
                  const payerName = nameMap[activity.paidBy] ?? `User ${activity.paidBy}`;
                  const groupName = getGroupName(activity.groupId);
                  const mySplit = activity.splits?.find((s) => s.userId === currentUserId);
                  const myShare = mySplit ? mySplit.shareAmount : 0;

                  let title = "";
                  let subdetail = "";
                  let amountLabel = "";
                  let amountColor = "text-slate-900";
                  let badgeBg = "bg-slate-100 text-slate-700";
                  let Icon = Receipt;

                  if (isPayer) {
                    const lentAmount = activity.amount - myShare;
                    title = `You paid for "${activity.description}"`;
                    subdetail = `You paid $${activity.amount.toFixed(2)} (you lent $${lentAmount.toFixed(2)})`;
                    amountLabel = `+ $${lentAmount.toFixed(2)}`;
                    amountColor = "text-emerald-600 font-bold";
                    badgeBg = "bg-emerald-50 text-emerald-700 border border-emerald-100";
                    Icon = ArrowUpRight;
                  } else if (mySplit) {
                    title = `${payerName} paid for "${activity.description}"`;
                    subdetail = `You owe $${myShare.toFixed(2)} to ${payerName}`;
                    amountLabel = `- $${myShare.toFixed(2)}`;
                    amountColor = "text-rose-600 font-bold";
                    badgeBg = "bg-rose-50 text-rose-700 border border-rose-100";
                    Icon = ArrowDownLeft;
                  } else {
                    title = `${payerName} paid for "${activity.description}"`;
                    subdetail = "You are not involved in this expense";
                    amountLabel = "$0.00";
                    amountColor = "text-slate-500 font-semibold";
                    badgeBg = "bg-slate-100 text-slate-500";
                    Icon = Info;
                  }

                  return (
                    <Card
                      key={`expense-${activity.id}`}
                      className="hover:translate-y-[-2px] hover:shadow-lg transition-all duration-300 border-border"
                    >
                      <CardContent className="p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                        {/* Left: Info */}
                        <div className="flex items-center gap-4">
                          <div className={`p-3 rounded-xl ${badgeBg} shadow-sm shrink-0`}>
                            <Icon size={24} />
                          </div>
                          <div className="space-y-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="font-bold text-foreground text-base">{title}</span>
                              {/* Group Pill (Clickable) */}
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  navigate(`/groups/${activity.groupId}`);
                                }}
                                className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300 hover:bg-indigo-100 transition-colors border border-indigo-100 dark:border-indigo-800"
                                title="Go to group details"
                              >
                                {groupName}
                              </button>
                            </div>

                            <p className="text-sm font-medium text-muted-foreground">{subdetail}</p>

                            {/* Date and Time */}
                            <div className="flex items-center gap-3 text-xs text-muted-foreground font-medium pt-1">
                              <span className="flex items-center gap-1">
                                <Calendar size={12} /> {date}
                              </span>
                              <span className="flex items-center gap-1">
                                <Clock size={12} /> {time}
                              </span>
                            </div>
                          </div>
                        </div>

                        {/* Right: Net Amount Visual */}
                        <div className="text-left sm:text-right shrink-0">
                          <div className={`text-lg ${amountColor}`}>{amountLabel}</div>
                          <div className="text-xs text-muted-foreground font-semibold mt-0.5">
                            Total Expense: ${activity.amount.toFixed(2)}
                          </div>
                        </div>
                      </CardContent>
                    </Card>
                  );
                }

                // ------------------ SETTLEMENT TYPE RENDERING ------------------
                const isPayer = activity.payerId === currentUserId;
                const isPayee = activity.payeeId === currentUserId;
                const payerName = nameMap[activity.payerId] ?? `User ${activity.payerId}`;
                const payeeName = nameMap[activity.payeeId] ?? `User ${activity.payeeId}`;

                let title = "";
                let subdetail = "";
                let amountLabel = "";
                let amountColor = "text-slate-900";
                let badgeBg = "bg-slate-100 text-slate-700";
                let Icon = ArrowRightLeft;

                if (isPayer) {
                  title = `You sent a payment to ${payeeName}`;
                  subdetail = `You paid $${activity.amount.toFixed(2)} to ${payeeName}`;
                  amountLabel = `+ $${activity.amount.toFixed(2)}`;
                  amountColor = "text-emerald-600 font-bold";
                  badgeBg = "bg-emerald-50 text-emerald-700 border border-emerald-100";
                  Icon = ArrowUpRight;
                } else if (isPayee) {
                  title = `${payerName} sent you a payment`;
                  subdetail = `You received $${activity.amount.toFixed(2)} from ${payerName}`;
                  amountLabel = `-$${activity.amount.toFixed(2)}`;
                  amountColor = "text-emerald-600 font-bold"; // received is also positive financially to reduce receivables
                  badgeBg = "bg-teal-50 text-teal-700 border border-teal-100";
                  Icon = ArrowDownLeft;
                } else {
                  title = `${payerName} paid ${payeeName}`;
                  subdetail = `Settled $${activity.amount.toFixed(2)}`;
                  amountLabel = `$${activity.amount.toFixed(2)}`;
                  amountColor = "text-slate-500 font-semibold";
                  badgeBg = "bg-slate-100 text-slate-500";
                  Icon = ArrowRightLeft;
                }

                return (
                  <Card
                    key={`settlement-${activity.id}`}
                    className="hover:translate-y-[-2px] hover:shadow-lg transition-all duration-300 border-border"
                  >
                    <CardContent className="p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                      {/* Left: Info */}
                      <div className="flex items-center gap-4">
                        <div className={`p-3 rounded-xl ${badgeBg} shadow-sm shrink-0`}>
                          <Icon size={24} />
                        </div>
                        <div className="space-y-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="font-bold text-foreground text-base">{title}</span>
                            {/* If group is associated, render group link */}
                            {activity.groupId && (
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  navigate(`/groups/${activity.groupId}`);
                                }}
                                className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300 hover:bg-indigo-100 transition-colors border border-indigo-100 dark:border-indigo-800"
                                title="Go to group details"
                              >
                                {getGroupName(activity.groupId)}
                              </button>
                            )}

                            {!activity.groupId && (
                              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-indigo-50 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-800 flex items-center gap-1 shadow-sm uppercase tracking-wide">
                                <Globe size={11} className="text-indigo-500 dark:text-indigo-400" /> Direct
                              </span>
                            )}

                            {/* Status Badge */}
                            <span className={`px-2 py-0.5 text-[10px] font-bold rounded uppercase ${activity.status === "COMPLETED"
                              ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-300"
                              : activity.status === "MARKED_PAID"
                                ? "bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300"
                                : "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300"
                              }`}>
                              {activity.status.replace("_", " ")}
                            </span>
                          </div>

                          <p className="text-sm font-medium text-muted-foreground">{subdetail}</p>

                          {/* Date and Time */}
                          <div className="flex items-center gap-3 text-xs text-muted-foreground font-medium pt-1">
                            <span className="flex items-center gap-1">
                              <Calendar size={12} /> {date}
                            </span>
                            <span className="flex items-center gap-1">
                              <Clock size={12} /> {time}
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* Right: Net Amount Visual */}
                      <div className="text-left sm:text-right shrink-0">
                        <div className={`text-lg ${amountColor}`}>{amountLabel}</div>
                        <div className="text-xs text-muted-foreground font-semibold mt-0.5">
                          Settlement Record
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          </ScrollArea>
        )}
      </div>
    </DashboardLayout>
  );
};

export default ActivityPage;
