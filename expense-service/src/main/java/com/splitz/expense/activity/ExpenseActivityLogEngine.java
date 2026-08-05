package com.splitz.expense.activity;

import com.splitz.expense.dto.ActivityLogDTO;
import com.splitz.expense.model.Expense;
import java.util.List;

/**
 * Deep module interface for expense audit logging and diff generation. Encapsulates diff
 * calculation, activity log construction, and repository persistence.
 */
public interface ExpenseActivityLogEngine {

  void recordCreated(Expense expense, Long actorId);

  void recordUpdated(
      Expense oldExpense, Expense updatedExpense, SplitChange splitChange, Long actorId);

  void recordDeleted(Expense expense, Long actorId);

  List<ActivityLogDTO> getGroupActivity(Long groupId);
}
