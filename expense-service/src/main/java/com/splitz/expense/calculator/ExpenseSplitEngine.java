package com.splitz.expense.calculator;

import com.splitz.expense.activity.SplitChange;
import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.SplitType;
import java.util.List;

/**
 * Deep engine that encapsulates expense split calculation, split type fallbacks, and entity
 * collection reconciliation.
 */
public interface ExpenseSplitEngine {

  /** Calculates and creates initial ExpenseSplit entities for a new Expense. */
  List<ExpenseSplit> applyInitialSplits(
      Expense expense, List<SplitRequest> splitRequests, SplitType splitType);

  /** Reconciles and updates ExpenseSplit entities on an existing Expense based on SplitChange. */
  void reconcileSplits(
      Expense expense,
      SplitChange splitChange,
      List<SplitRequest> requestedSplits,
      SplitType requestedSplitType);
}
