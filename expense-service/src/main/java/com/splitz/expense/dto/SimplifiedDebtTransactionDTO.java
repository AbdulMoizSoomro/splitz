package com.splitz.expense.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single suggested transfer produced by the debt simplification engine. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimplifiedDebtTransactionDTO {
  private Long fromUserId;
  private String fromUsername;
  private Long toUserId;
  private String toUsername;
  private BigDecimal amount;
  private String status;
}
