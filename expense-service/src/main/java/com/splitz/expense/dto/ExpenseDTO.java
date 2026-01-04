package com.splitz.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseDTO {

    private Long id;
    private Long groupId;
    private String description;
    private BigDecimal amount;
    private String currency;
    private Long paidBy;
    private Long categoryId;
    private LocalDate expenseDate;
    private String notes;
    private String receiptUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
