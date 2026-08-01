import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { simplificationService } from "./simplificationService";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import type { UpdateSimplificationSettingsRequest } from "../../types/simplification";
import { Settings, Shield, UserX, Globe, Users, Loader2 } from "lucide-react";
import { toast } from "sonner";

interface SimplificationSettingsCardProps {
  groupId: number;
  isAdminOrOwner: boolean;
  currentUserId: number;
}

export const SimplificationSettingsCard = ({
  groupId,
  isAdminOrOwner,
  currentUserId,
}: SimplificationSettingsCardProps) => {
  const queryClient = useQueryClient();

  const { data: settings, isLoading } = useQuery({
    queryKey: ["group-simplification-settings", groupId],
    queryFn: () => simplificationService.getSimplificationSettings(groupId),
  });

  const updateSettingsMutation = useMutation({
    mutationFn: (
      data: UpdateSimplificationSettingsRequest,
    ) =>
      simplificationService.updateSimplificationSettings(groupId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-simplification-settings", groupId] });
      queryClient.invalidateQueries({ queryKey: ["group-simplification-plan", groupId] });
      queryClient.invalidateQueries({ queryKey: ["group-balances", groupId] });
      toast.success("Debt simplification settings updated");
    },
    onError: () => {
      toast.error("Failed to update simplification settings");
    },
  });

  const optOutMutation = useMutation({
    mutationFn: (optOut: boolean) =>
      simplificationService.toggleUserOptOut(groupId, { optOut }),
    onSuccess: (_, optOut) => {
      queryClient.invalidateQueries({ queryKey: ["group-simplification-settings", groupId] });
      queryClient.invalidateQueries({ queryKey: ["group-simplification-plan", groupId] });
      queryClient.invalidateQueries({ queryKey: ["group-balances", groupId] });
      toast.success(
        optOut
          ? "You have opted out of debt simplification for this group"
          : "You have opted back in to debt simplification",
      );
    },
    onError: () => {
      toast.error("Failed to update opt-out status");
    },
  });

  if (isLoading) {
    return (
      <Card>
        <CardContent className="flex justify-center p-6" data-testid="settings-loader">
          <Loader2 className="animate-spin text-blue-600" size={24} />
        </CardContent>
      </Card>
    );
  }

  if (!settings) return null;

  const isOptedOut = settings.optOutUserIds?.includes(currentUserId) ?? false;

  return (
    <Card className="border-border shadow-sm">
      <CardHeader className="pb-3">
        <CardTitle className="text-foreground flex items-center justify-between text-lg">
          <div className="flex items-center gap-2">
            <Settings size={20} className="text-blue-600" />
            <span>Debt Simplification & Governance</span>
          </div>
          {settings.simplificationEnabled ? (
            <Badge className="bg-emerald-500/10 text-emerald-600 border-emerald-500/20">
              Active ({settings.simplificationScope === "CROSS_GROUP" ? "Global" : "Intra-Group"})
            </Badge>
          ) : (
            <Badge variant="outline" className="text-muted-foreground">
              Disabled
            </Badge>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Admin Governance Section */}
        {isAdminOrOwner && (
          <div className="space-y-4 pt-1 border-b border-border pb-5">
            <div className="flex items-center justify-between">
              <div className="space-y-0.5">
                <label className="text-sm font-medium text-foreground flex items-center gap-2 cursor-pointer" htmlFor="enable-simplification">
                  <Shield size={16} className="text-purple-600" />
                  Enable Smart Debt Reduction
                </label>
                <p className="text-xs text-muted-foreground">
                  Automatically consolidate transitive debts between members to minimize payments.
                </p>
              </div>
              <Switch
                id="enable-simplification"
                data-testid="enable-simplification-switch"
                checked={settings.simplificationEnabled}
                onCheckedChange={(checked) =>
                  updateSettingsMutation.mutate({
                    simplificationEnabled: checked,
                    simplificationScope: settings.simplificationScope,
                  })
                }
                disabled={updateSettingsMutation.isPending}
                aria-label="Enable Debt Simplification"
              />
            </div>

            {settings.simplificationEnabled && (
              <div className="space-y-2 pt-2">
                <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Simplification Scope
                </span>
                <div className="grid grid-cols-2 gap-3">
                  <Button
                    type="button"
                    variant={settings.simplificationScope === "INTRA_GROUP" ? "default" : "outline"}
                    size="sm"
                    className="flex items-center justify-center gap-2 text-xs"
                    onClick={() =>
                      updateSettingsMutation.mutate({
                        simplificationEnabled: settings.simplificationEnabled,
                        simplificationScope: "INTRA_GROUP",
                      })
                    }
                    disabled={updateSettingsMutation.isPending}
                    aria-label="Select Intra-Group Scope"
                  >
                    <Users size={14} />
                    <span>Intra-Group Only</span>
                  </Button>
                  <Button
                    type="button"
                    variant={settings.simplificationScope === "CROSS_GROUP" ? "default" : "outline"}
                    size="sm"
                    className="flex items-center justify-center gap-2 text-xs"
                    onClick={() =>
                      updateSettingsMutation.mutate({
                        simplificationEnabled: settings.simplificationEnabled,
                        simplificationScope: "CROSS_GROUP",
                      })
                    }
                    disabled={updateSettingsMutation.isPending}
                    aria-label="Select Global Cross-Group Scope"
                  >
                    <Globe size={14} />
                    <span>Global Multi-Group</span>
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Self-Service User Opt-Out Section */}
        <div className="flex items-center justify-between">
          <div className="space-y-0.5">
            <label className="text-sm font-medium text-foreground flex items-center gap-2 cursor-pointer" htmlFor="user-opt-out">
              <UserX size={16} className="text-amber-600" />
              Opt Out of Debt Netting
            </label>
            <p className="text-xs text-muted-foreground">
              When opted out, your debts will not be transferred or simplified with third parties.
            </p>
          </div>
          <Switch
            id="user-opt-out"
            data-testid="user-opt-out-switch"
            checked={isOptedOut}
            onCheckedChange={(checked) => optOutMutation.mutate(checked)}
            disabled={optOutMutation.isPending}
            aria-label="Opt out of debt simplification"
          />
        </div>

        {isOptedOut && (
          <div className="p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg text-amber-700 dark:text-amber-400 text-xs">
            <p className="font-semibold mb-1">Opt-Out Active</p>
            You are currently excluded from automated debt transfers. You will settle payments directly with original counterparties.
          </div>
        )}
      </CardContent>
    </Card>
  );
};
