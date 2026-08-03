package com.splitz.expense.activity;

import com.splitz.expense.activity.ExpenseChange.SplitChange;
import com.splitz.expense.model.Category;
import com.splitz.expense.model.Expense;
import org.springframework.stereotype.Component;

/**
 * Pure diff renderer for expense updates. Owns the human-readable change summary that is persisted
 * as an activity-log {@code details} value. Deliberately free of Spring, repositories and mutation
 * logic.
 */
@Component
public class ExpenseDiffCalculator {

  public String calculate(Expense oldExpense, ExpenseChange change) {
    StringBuilder diff = new StringBuilder();
    if (change.description() != null && !change.description().equals(oldExpense.getDescription())) {
      diff.append("description: ")
          .append(oldExpense.getDescription())
          .append(" -> ")
          .append(change.description())
          .append("; ");
    }
    if (change.amount() != null && change.amount().compareTo(oldExpense.getAmount()) != 0) {
      diff.append("amount: ")
          .append(String.format("%.2f", oldExpense.getAmount()))
          .append(" -> ")
          .append(String.format("%.2f", change.amount()))
          .append("; ");
    }
    if (change.currency() != null && !change.currency().equals(oldExpense.getCurrency())) {
      diff.append("currency: ")
          .append(oldExpense.getCurrency())
          .append(" -> ")
          .append(change.currency())
          .append("; ");
    }
    if (change.paidBy() != null && !change.paidBy().equals(oldExpense.getPaidBy())) {
      diff.append("paidBy changed; ");
    }
    Category oldCategory = oldExpense.getCategory();
    if (change.categoryId() != null
        && (oldCategory == null || !change.categoryId().equals(oldCategory.getId()))) {
      diff.append("category: ")
          .append(oldCategory != null ? oldCategory.getName() : "None")
          .append(" -> ")
          .append(change.categoryName())
          .append("; ");
    }
    if (change.expenseDate() != null && !change.expenseDate().equals(oldExpense.getExpenseDate())) {
      diff.append("date changed; ");
    }
    if (change.notes() != null && !change.notes().equals(oldExpense.getNotes())) {
      diff.append("notes updated; ");
    }
    if (change.receiptUrl() != null && !change.receiptUrl().equals(oldExpense.getReceiptUrl())) {
      diff.append("receipt updated; ");
    }
    if (change.splitChange() == SplitChange.MODIFIED) {
      diff.append("splits: modified; ");
    } else if (change.splitChange() == SplitChange.RECALCULATED) {
      diff.append("splits: recalculated; ");
    }
    return diff.toString().trim();
  }
}
