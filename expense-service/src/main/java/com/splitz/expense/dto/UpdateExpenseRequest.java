package com.splitz.expense.dto;

import com.splitz.expense.model.SplitType;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateExpenseRequest {

  private String description;

  @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
  private BigDecimal amount;

  private String currency;

  private Long paidBy;

  private Long categoryId;

  private LocalDate expenseDate;

  private String notes;

  private String receiptUrl;

  private SplitType splitType;

  private List<SplitRequest> splits;
}
