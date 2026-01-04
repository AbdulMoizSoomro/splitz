package com.splitz.expense.dto;

import com.splitz.expense.model.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementDTO {

  private Long id;
  private Long groupId;
  private Long payerId;
  private Long payeeId;
  private BigDecimal amount;
  private SettlementStatus status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime markedPaidAt;
  private LocalDateTime settledAt;
}
