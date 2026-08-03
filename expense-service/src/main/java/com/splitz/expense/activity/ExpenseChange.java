package com.splitz.expense.activity;

import com.splitz.expense.dto.UpdateExpenseRequest;
import com.splitz.expense.model.Category;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Declarative description of which expense fields were updated and how. */
public record ExpenseChange(
    String description,
    BigDecimal amount,
    String currency,
    Long paidBy,
    Long categoryId,
    String categoryName,
    LocalDate expenseDate,
    String notes,
    String receiptUrl,
    SplitChange splitChange) {

  public enum SplitChange {
    NONE,
    MODIFIED,
    RECALCULATED
  }

  public static ExpenseChange fromRequest(
      UpdateExpenseRequest request, Category newCategory, SplitChange splitChange) {
    return ExpenseChange.builder()
        .description(request.getDescription())
        .amount(request.getAmount())
        .currency(request.getCurrency())
        .paidBy(request.getPaidBy())
        .categoryId(request.getCategoryId())
        .categoryName(newCategory != null ? newCategory.getName() : null)
        .expenseDate(request.getExpenseDate())
        .notes(request.getNotes())
        .receiptUrl(request.getReceiptUrl())
        .splitChange(splitChange)
        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String description;
    private BigDecimal amount;
    private String currency;
    private Long paidBy;
    private Long categoryId;
    private String categoryName;
    private LocalDate expenseDate;
    private String notes;
    private String receiptUrl;
    private SplitChange splitChange = SplitChange.NONE;

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    public Builder currency(String currency) {
      this.currency = currency;
      return this;
    }

    public Builder paidBy(Long paidBy) {
      this.paidBy = paidBy;
      return this;
    }

    public Builder categoryId(Long categoryId) {
      this.categoryId = categoryId;
      return this;
    }

    public Builder categoryName(String categoryName) {
      this.categoryName = categoryName;
      return this;
    }

    public Builder expenseDate(LocalDate expenseDate) {
      this.expenseDate = expenseDate;
      return this;
    }

    public Builder notes(String notes) {
      this.notes = notes;
      return this;
    }

    public Builder receiptUrl(String receiptUrl) {
      this.receiptUrl = receiptUrl;
      return this;
    }

    public Builder splitChange(SplitChange splitChange) {
      this.splitChange = splitChange;
      return this;
    }

    public ExpenseChange build() {
      return new ExpenseChange(
          description,
          amount,
          currency,
          paidBy,
          categoryId,
          categoryName,
          expenseDate,
          notes,
          receiptUrl,
          splitChange);
    }
  }
}
