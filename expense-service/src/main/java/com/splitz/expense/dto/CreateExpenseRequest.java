package com.splitz.expense.dto;

import com.splitz.expense.model.SplitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateExpenseRequest {

  @NotBlank(message = "Description is required")
  private String description;

  @NotNull(message = "Amount is required")
  @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
  private BigDecimal amount;

  @Builder.Default private String currency = "EUR";

  @NotNull(message = "Payer ID is required")
  private Long paidBy;

  private Long categoryId;

  private LocalDate expenseDate;

  private String notes;

  private String receiptUrl;

  @NotNull(message = "Split type is required")
  private SplitType splitType;

  private List<SplitRequest> splits;
}
