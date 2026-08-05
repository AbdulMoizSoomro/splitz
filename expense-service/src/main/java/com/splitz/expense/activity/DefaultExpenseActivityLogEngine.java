package com.splitz.expense.activity;

import com.splitz.expense.dto.ActivityLogDTO;
import com.splitz.expense.mapper.ActivityLogMapper;
import com.splitz.expense.model.ActivityLog;
import com.splitz.expense.model.ActivityLogType;
import com.splitz.expense.model.Category;
import com.splitz.expense.model.Expense;
import com.splitz.expense.repository.ActivityLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultExpenseActivityLogEngine implements ExpenseActivityLogEngine {

  private final ActivityLogRepository activityLogRepository;
  private final ActivityLogMapper activityLogMapper;

  @Override
  @Transactional
  public void recordCreated(Expense expense, Long actorId) {
    ActivityLog log =
        ActivityLog.builder()
            .groupId(expense.getGroup().getId())
            .type(ActivityLogType.EXPENSE_CREATED)
            .actorId(actorId)
            .entityId(expense.getId())
            .entityName(expense.getDescription())
            .details(null)
            .build();
    activityLogRepository.save(log);
  }

  @Override
  @Transactional
  public void recordUpdated(
      Expense oldExpense, Expense updatedExpense, SplitChange splitChange, Long actorId) {
    ActivityLog log =
        ActivityLog.builder()
            .groupId(updatedExpense.getGroup().getId())
            .type(ActivityLogType.EXPENSE_UPDATED)
            .actorId(actorId)
            .entityId(updatedExpense.getId())
            .entityName(updatedExpense.getDescription())
            .details(calculateDiff(oldExpense, updatedExpense, splitChange))
            .build();
    activityLogRepository.save(log);
  }

  @Override
  @Transactional
  public void recordDeleted(Expense expense, Long actorId) {
    ActivityLog log =
        ActivityLog.builder()
            .groupId(expense.getGroup().getId())
            .type(ActivityLogType.EXPENSE_DELETED)
            .actorId(actorId)
            .entityId(expense.getId())
            .entityName(expense.getDescription())
            .details(null)
            .build();
    activityLogRepository.save(log);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActivityLogDTO> getGroupActivity(Long groupId) {
    return activityLogMapper.toDTOList(
        activityLogRepository.findByGroupIdOrderByTimestampDesc(groupId));
  }

  private String calculateDiff(
      Expense oldExpense, Expense updatedExpense, SplitChange splitChange) {
    StringBuilder diff = new StringBuilder();
    String oldDescription = oldExpense.getDescription();
    String newDescription = updatedExpense.getDescription();
    if (newDescription != null && !newDescription.equals(oldDescription)) {
      diff.append("description: ")
          .append(oldDescription)
          .append(" -> ")
          .append(newDescription)
          .append("; ");
    }
    BigDecimal oldAmount = oldExpense.getAmount();
    BigDecimal newAmount = updatedExpense.getAmount();
    if (newAmount != null && newAmount.compareTo(oldAmount) != 0) {
      diff.append("amount: ")
          .append(String.format("%.2f", oldAmount))
          .append(" -> ")
          .append(String.format("%.2f", newAmount))
          .append("; ");
    }
    String oldCurrency = oldExpense.getCurrency();
    String newCurrency = updatedExpense.getCurrency();
    if (newCurrency != null && !newCurrency.equals(oldCurrency)) {
      diff.append("currency: ").append(oldCurrency).append(" -> ").append(newCurrency).append("; ");
    }
    Long oldPaidBy = oldExpense.getPaidBy();
    Long newPaidBy = updatedExpense.getPaidBy();
    if (newPaidBy != null && !newPaidBy.equals(oldPaidBy)) {
      diff.append("paidBy changed; ");
    }
    Category oldCategory = oldExpense.getCategory();
    Category newCategory = updatedExpense.getCategory();
    if (newCategory != null
        && (oldCategory == null || !newCategory.getId().equals(oldCategory.getId()))) {
      diff.append("category: ")
          .append(oldCategory != null ? oldCategory.getName() : "None")
          .append(" -> ")
          .append(newCategory.getName())
          .append("; ");
    }
    LocalDate oldExpenseDate = oldExpense.getExpenseDate();
    LocalDate newExpenseDate = updatedExpense.getExpenseDate();
    if (newExpenseDate != null && !newExpenseDate.equals(oldExpenseDate)) {
      diff.append("date changed; ");
    }
    String oldNotes = oldExpense.getNotes();
    String newNotes = updatedExpense.getNotes();
    if (newNotes != null && !newNotes.equals(oldNotes)) {
      diff.append("notes updated; ");
    }
    String oldReceiptUrl = oldExpense.getReceiptUrl();
    String newReceiptUrl = updatedExpense.getReceiptUrl();
    if (newReceiptUrl != null && !newReceiptUrl.equals(oldReceiptUrl)) {
      diff.append("receipt updated; ");
    }
    if (splitChange == SplitChange.MODIFIED) {
      diff.append("splits: modified; ");
    } else if (splitChange == SplitChange.RECALCULATED) {
      diff.append("splits: recalculated; ");
    }
    return diff.toString().trim();
  }
}
