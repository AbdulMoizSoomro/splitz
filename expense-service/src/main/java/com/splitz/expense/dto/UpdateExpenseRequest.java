package com.splitz.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;
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

  private Long categoryId;

  private LocalDate expenseDate;

  private String notes;

  private String receiptUrl;
}
