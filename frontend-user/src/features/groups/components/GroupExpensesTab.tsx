import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import { TabsContent } from "@/components/ui/tabs";
import { Loader2, Plus, Receipt, Calendar, MoreVertical } from "lucide-react";
import type { Expense } from "../../../types/expense";
import type { Category } from "../../expenses/categoryService";
import type { GroupGovernance } from "../membershipGating";

interface GroupExpensesTabProps {
  expenses?: Expense[];
  sortedExpenses: Expense[];
  isExpensesLoading: boolean;
  categories?: Category[];
  memberNames: Record<number, string>;
  userId?: number;
  governance: GroupGovernance;
  onAddExpense: () => void;
  onEditExpense: (expense: Expense) => void;
  onDeleteExpense: (expense: Expense) => void;
}

export function GroupExpensesTab({
  expenses,
  sortedExpenses,
  isExpensesLoading,
  categories,
  memberNames,
  userId,
  governance,
  onAddExpense,
  onEditExpense,
  onDeleteExpense,
}: GroupExpensesTabProps) {
  return (
    <TabsContent value="expenses" className="h-full flex flex-col min-h-0 space-y-4">
      <div className="flex justify-between items-center px-1 shrink-0">
        <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
          <Receipt size={20} className="text-blue-600" />
          <span>Group Expenses</span>
        </h2>
        <Button
          size="sm"
          onClick={onAddExpense}
          className="flex items-center gap-2"
        >
          <Plus size={18} />
          <span>Add Expense</span>
        </Button>
      </div>

      {isExpensesLoading ? (
        <div className="flex justify-center p-8" data-testid="loader">
          <Loader2 className="animate-spin text-blue-600" size={24} />
        </div>
      ) : !expenses || expenses.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center">
            <Receipt className="mx-auto text-gray-300 mb-4" size={48} />
            <h3 className="text-lg font-medium text-foreground mb-1">
              No expenses yet
            </h3>
            <p className="text-muted-foreground italic mb-6">
              Add an expense to get started splitting with the group!
            </p>
            <Button
              onClick={onAddExpense}
              className="flex items-center gap-2 mx-auto"
            >
              <Plus size={18} />
              <span>Add Expense</span>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <ScrollArea className="h-[500px] pr-4">
          <div className="space-y-3">
            {sortedExpenses.map((expense) => {
              const isPayer = expense.paidBy === userId;
              const payerName = isPayer
                ? "You"
                : (memberNames[expense.paidBy] ?? `User ${expense.paidBy}`);
              const date = new Date(expense.expenseDate);
              const mySplit = expense.splits.find((s) => s.userId === userId);
              const canManage = governance.canManageExpense(expense);

              const category = categories?.find((c) => c.id === expense.categoryId);
              const categoryName = category?.name ?? "General";

              let balanceIndicator = null;
              if (isPayer) {
                const lentAmount = expense.splits
                  .filter((s) => s.userId !== userId)
                  .reduce((sum, s) => sum + s.shareAmount, 0);
                if (lentAmount > 0) {
                  balanceIndicator = (
                    <span className="text-green-600 font-semibold text-sm">
                      you lent <span className="font-bold">${lentAmount.toFixed(2)}</span>
                    </span>
                  );
                } else {
                  balanceIndicator = (
                    <span className="text-muted-foreground text-sm">
                      you paid for yourself
                    </span>
                  );
                }
              } else {
                if (mySplit) {
                  balanceIndicator = (
                    <span className="text-red-500 font-semibold text-sm">
                      you owe {payerName}{" "}
                      <span className="font-bold">${mySplit.shareAmount.toFixed(2)}</span>
                    </span>
                  );
                } else {
                  balanceIndicator = (
                    <span className="text-muted-foreground text-sm">
                      not involved
                    </span>
                  );
                }
              }

              return (
                <Card
                  key={`expense-${expense.id}`}
                  className="hover:shadow-md transition-shadow"
                >
                  <CardContent className="p-4 flex items-center justify-between">
                    <div className="flex items-center gap-4 flex-1 min-w-0">
                      <div className="w-10 h-10 rounded-full bg-blue-50 flex items-center justify-center text-blue-600 shrink-0">
                        <Receipt size={20} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <h3 className="font-semibold text-foreground truncate">
                          {expense.description}
                        </h3>
                        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-0.5 text-xs text-muted-foreground">
                          <span>
                            Paid by{" "}
                            <span className="font-medium text-gray-700">
                              {payerName}
                            </span>
                          </span>
                          <span>•</span>
                          <span className="flex items-center gap-1">
                            <Calendar size={12} />
                            {date.toLocaleDateString()}
                          </span>
                          <span>•</span>
                          <Badge className="bg-muted text-foreground border-border hover:bg-muted/80">
                            {categoryName}
                          </Badge>
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-6 shrink-0">
                      <div className="text-right">
                        <div className="text-base font-bold text-foreground">
                          ${expense.amount.toFixed(2)}
                        </div>
                        <div>{balanceIndicator}</div>
                      </div>

                      {canManage && (
                        <DropdownMenu>
                          <DropdownMenuTrigger
                            className="p-1 hover:bg-muted rounded-full text-muted-foreground hover:text-muted-foreground transition-colors"
                            aria-label={`Actions for ${expense.description}`}
                          >
                            <MoreVertical size={20} />
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => onEditExpense(expense)}>
                              Edit
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              variant="destructive"
                              onClick={() => onDeleteExpense(expense)}
                            >
                              Delete
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      )}
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        </ScrollArea>
      )}
    </TabsContent>
  );
}
