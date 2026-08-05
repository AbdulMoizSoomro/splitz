import { Link } from "react-router-dom";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Receipt,
  DollarSign,
  Globe,
  Pencil,
  Check,
  X,
  Loader2,
  Users,
} from "lucide-react";
import { isGlobalPayment as isGlobalPaymentFor } from "../../balances/settlement";
import type { User, FriendshipSettlementDTO } from "../../types/user";

interface UnifiedActivityItem {
  type: "expense" | "settlement";
  date: string;
  data: any;
}

interface FriendActivityTabProps {
  unifiedActivity: UnifiedActivityItem[];
  currentUserId?: number;
  friend: User;
  friendId: number;
  editingSettlementId: number | null;
  editAmount: string;
  groupNameMap: Record<number, string>;
  onStartEditing: (settlement: FriendshipSettlementDTO) => void;
  onCancelEditing: () => void;
  onSubmitEdit: (settlementId: number) => void;
  onConfirmSettlement: (settlementId: number) => void;
  confirmSettlementPending: boolean;
  updateSettlementPending: boolean;
  onSetEditAmount: (val: string) => void;
}

export function FriendActivityTab({
  unifiedActivity,
  currentUserId,
  friend,
  friendId,
  editingSettlementId,
  editAmount,
  groupNameMap,
  onStartEditing,
  onCancelEditing,
  onSubmitEdit,
  onConfirmSettlement,
  confirmSettlementPending,
  updateSettlementPending,
  onSetEditAmount,
}: FriendActivityTabProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Shared Activity</CardTitle>
      </CardHeader>
      <CardContent>
        {unifiedActivity.length > 0 ? (
          <div className="space-y-3">
            {unifiedActivity.map((activity, index) => {
              if (activity.type === "expense") {
                const expense = activity.data;
                return (
                  <Card
                    key={`expense-${expense.id}-${index}`}
                    className="border-border shadow-sm"
                  >
                    <CardContent className="flex items-center justify-between p-3">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center text-blue-600 dark:text-blue-400">
                          <Receipt size={20} />
                        </div>
                        <div>
                          <p className="font-medium text-foreground">
                            {expense.description}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {new Date(
                              expense.expenseDate,
                            ).toLocaleDateString()}
                          </p>
                        </div>
                      </div>
                      <div className="text-right">
                        <p className="font-bold text-foreground">
                          {expense.currency} {expense.amount.toFixed(2)}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          Paid by{" "}
                          {expense.paidBy === friendId
                            ? friend.firstName
                            : "You"}
                        </p>
                      </div>
                    </CardContent>
                  </Card>
                );
              } else {
                const settlement: FriendshipSettlementDTO = activity.data;
                const isPayer = settlement.payerId === currentUserId;
                const isEditing = editingSettlementId === settlement.id;
                const canEdit = settlement.status !== "COMPLETED";

                let badgeClassName =
                  "bg-gray-100 text-gray-800 hover:bg-gray-100/80 border-gray-200";
                if (settlement.status === "COMPLETED") {
                  badgeClassName =
                    "bg-green-100 text-green-800 border-green-200 hover:bg-green-100/80";
                } else if (settlement.status === "MARKED_PAID") {
                  badgeClassName =
                    "bg-yellow-100 text-yellow-800 border-yellow-200 hover:bg-yellow-100/80";
                }

                const isGlobalPayment = isGlobalPaymentFor(settlement);

                return (
                  <Card
                    key={`settlement-${settlement.id}-${index}`}
                    className="shadow-sm hover:shadow-md transition-shadow border-border"
                  >
                    <CardContent className="p-3">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-full bg-green-100 dark:bg-green-900/30 flex items-center justify-center text-green-600 dark:text-green-400">
                            <DollarSign size={20} />
                          </div>
                          <div className="flex-1">
                            <div className="flex items-center gap-2 flex-wrap">
                              <p className="font-medium text-foreground">
                                {isPayer
                                  ? `You paid ${friend.firstName}`
                                  : `${friend.firstName} paid you`}
                              </p>
                              <Badge className={badgeClassName}>
                                {settlement.status === "MARKED_PAID"
                                  ? "Pending Confirmation"
                                  : settlement.status === "COMPLETED"
                                    ? "Settled"
                                    : "Pending"}
                              </Badge>
                              {isGlobalPayment && (
                                <span className="px-2 py-0.5 text-[9px] font-bold rounded bg-indigo-50 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-800 flex items-center gap-1 shadow-sm uppercase tracking-wider">
                                  <Globe size={10} /> Direct
                                </span>
                              )}
                              {!isPayer &&
                                settlement.status === "MARKED_PAID" && (
                                  <Button
                                    variant="default"
                                    size="sm"
                                    onClick={() =>
                                      onConfirmSettlement(settlement.id)
                                    }
                                    disabled={confirmSettlementPending}
                                    className="h-6 py-0 px-2 text-[10px]"
                                  >
                                    {confirmSettlementPending ? (
                                      <Loader2
                                        className="animate-spin"
                                        size={12}
                                      />
                                    ) : (
                                      "Confirm Receipt"
                                    )}
                                  </Button>
                                )}
                              {canEdit && !isEditing && (
                                <button
                                  onClick={() => onStartEditing(settlement)}
                                  className="p-1 text-muted-foreground hover:text-blue-600 transition-colors rounded"
                                  title="Edit payment"
                                >
                                  <Pencil size={14} />
                                </button>
                              )}
                            </div>
                            <p className="text-xs text-muted-foreground">
                              {new Date(
                                settlement.createdAt,
                              ).toLocaleDateString()}{" "}
                              {new Date(
                                settlement.createdAt,
                              ).toLocaleTimeString([], {
                                hour: "2-digit",
                                minute: "2-digit",
                              })}
                            </p>
                          </div>
                        </div>
                        <div className="text-right">
                          {isEditing ? (
                            <div className="flex items-center gap-1">
                              <span className="text-muted-foreground">$</span>
                              <Input
                                type="number"
                                step="0.01"
                                min="0.01"
                                value={editAmount}
                                onChange={(e) => onSetEditAmount(e.target.value)}
                                className="w-20 text-right text-sm"
                                autoFocus
                              />
                              <button
                                onClick={() => onSubmitEdit(settlement.id)}
                                disabled={updateSettlementPending}
                                className="p-1 text-green-600 hover:text-green-700 transition-colors"
                                title="Save"
                              >
                                {updateSettlementPending ? (
                                  <Loader2 className="animate-spin" size={14} />
                                ) : (
                                  <Check size={14} />
                                )}
                              </button>
                              <button
                                onClick={onCancelEditing}
                                className="p-1 text-red-500 hover:text-red-600 transition-colors"
                                title="Cancel"
                              >
                                <X size={14} />
                              </button>
                            </div>
                          ) : (
                            <p className="font-bold text-foreground">
                              ${settlement.amount.toFixed(2)}
                            </p>
                          )}
                        </div>
                      </div>

                      {/* Group Allocations */}
                      {settlement.allocations &&
                      settlement.allocations.length > 0 ? (
                        <div className="mt-2 ml-13 pl-3 border-l-2 border-border space-y-1">
                          {settlement.allocations.map((alloc, aIdx) => (
                            <div
                              key={aIdx}
                              className="flex items-center justify-between text-xs text-muted-foreground py-0.5"
                            >
                              <span className="flex items-center gap-1.5">
                                {alloc.groupId ? (
                                  <>
                                    <Users
                                      size={10}
                                      className="text-muted-foreground"
                                    />
                                    <Link
                                      to={`/groups/${alloc.groupId}`}
                                      className="text-blue-600 hover:underline"
                                    >
                                      {groupNameMap[alloc.groupId] ||
                                        `Group #${alloc.groupId}`}
                                    </Link>
                                  </>
                                ) : (
                                  <>
                                    <Globe
                                      size={10}
                                      className="text-indigo-500"
                                    />
                                    <span className="text-indigo-600 font-semibold bg-indigo-50 px-1.5 py-0.5 rounded text-[10px]">
                                      Direct Personal Balance
                                    </span>
                                  </>
                                )}
                              </span>
                              <span className="font-medium text-foreground">
                                ${alloc.amount.toFixed(2)}
                              </span>
                            </div>
                          ))}
                        </div>
                      ) : (
                        /* Entirely Direct Payment indicator when no allocations array exists */
                        <div className="mt-2 ml-13 pl-3 border-l-2 border-border">
                          <div className="flex items-center justify-between text-xs text-muted-foreground py-0.5">
                            <span className="flex items-center gap-1.5">
                              <Globe size={10} className="text-indigo-500" />
                              <span className="text-indigo-600 dark:text-indigo-400 font-semibold bg-indigo-50 dark:bg-indigo-900/30 px-1.5 py-0.5 rounded text-[10px]">
                                Direct Personal Balance
                              </span>
                            </span>
                            <span className="font-medium text-foreground">
                              ${settlement.amount.toFixed(2)}
                            </span>
                          </div>
                        </div>
                      )}
                    </CardContent>
                  </Card>
                );
              }
            })}
          </div>
        ) : (
          <p className="text-muted-foreground italic">
            No shared activity found.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
