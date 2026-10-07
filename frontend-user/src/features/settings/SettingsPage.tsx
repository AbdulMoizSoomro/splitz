import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { simplificationService } from "../balances/simplificationService";
import { queryKeys } from "../../lib/queryKeys";
import DashboardLayout from "../../components/layout/DashboardLayout";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import { AlertTriangle, Loader2, Shield, UserX } from "lucide-react";
import { toast } from "sonner";

/**
 * Account-level debt simplification preference.
 *
 * An opt-out here is a hard override, not a default: it removes the user from suggested settlement
 * plans in every group they belong to, including groups configured before they opted out. Opting
 * back in here does not re-enable per-group opt-outs they previously set, so group controls still
 * apply independently.
 */
const SettingsPage = () => {
  const queryClient = useQueryClient();

  const { data: preference, isLoading } = useQuery({
    queryKey: queryKeys.simplificationPreference,
    queryFn: () => simplificationService.getAccountPreference(),
  });

  const mutation = useMutation({
    mutationFn: (optOut: boolean) => simplificationService.toggleAccountOptOut({ optOut }),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.simplificationPreference });
      // Every group's plan and balance view now resolves a different effective opt-out set.
      queryClient.invalidateQueries({ queryKey: ["group-simplification-plan"] });
      queryClient.invalidateQueries({ queryKey: ["group-balances"] });
      toast.success(
        updated.accountOptOut
          ? "You are opted out of debt netting in all groups"
          : "You are opted back in to debt netting",
      );
    },
    onError: () => {
      toast.error("Failed to update your debt simplification preference");
    },
  });

  const isOptedOut = preference?.accountOptOut ?? false;

  return (
    <DashboardLayout breadcrumbs={[{ label: "Settings" }]}>
      <div className="max-w-3xl mx-auto p-4 md:p-6 space-y-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
            Settings
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Preferences that apply to your account across every group.
          </p>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Shield className="text-purple-600" size={20} />
              Debt Simplification
            </CardTitle>
            <CardDescription>
              Splitz can suggest a shorter settlement path by netting out what you owe against what
              others owe you. Opting out keeps every one of your debts direct and unchanged.
            </CardDescription>
          </CardHeader>

          <CardContent>
            {isLoading ? (
              <div className="flex justify-center p-6" data-testid="preference-loader">
                <Loader2 className="animate-spin text-purple-600" size={24} />
              </div>
            ) : (
              <div className="space-y-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="space-y-1">
                    <label
                      htmlFor="account-opt-out"
                      className="text-sm font-medium flex items-center gap-2 cursor-pointer"
                    >
                      <UserX className="text-amber-600" size={16} />
                      Opt out of debt netting everywhere
                    </label>
                    <p className="text-xs text-muted-foreground">
                      Applies to all your groups immediately, including any configured before you
                      changed this.
                    </p>
                  </div>
                  <div className="flex items-center gap-3 shrink-0">
                    {isOptedOut && (
                      <Badge className="bg-amber-500/10 text-amber-700 border-amber-500/20">
                        Opted out
                      </Badge>
                    )}
                    <Switch
                      id="account-opt-out"
                      data-testid="account-opt-out-switch"
                      checked={isOptedOut}
                      onCheckedChange={(checked) => mutation.mutate(checked)}
                      disabled={mutation.isPending}
                      aria-label="Opt out of debt netting account-wide"
                    />
                  </div>
                </div>

                {isOptedOut && (
                  <div
                    className="flex items-start gap-2 p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg text-amber-700 dark:text-amber-400 text-xs"
                    data-testid="account-opt-out-notice"
                  >
                    <AlertTriangle size={16} className="shrink-0 mt-0.5 text-amber-600" />
                    <div>
                      <p className="font-semibold mb-1">Opt-out active on your account</p>
                      <p>
                        You are excluded from suggested settlement plans in every group. You will
                        settle directly with your original counterparties. Group admins can still
                        enable netting for everyone else.
                      </p>
                    </div>
                  </div>
                )}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
};

export { SettingsPage };
export default SettingsPage;