package com.splitz.expense.dto;

import com.splitz.expense.model.PaymentType;
import com.splitz.expense.model.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendshipSettlementDTO {

  private Long id;
  private PaymentType type;
  private Long payerId;
  private Long payeeId;
  private Long groupId;
  private BigDecimal amount;
  private SettlementStatus status;
  private String notes;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime markedPaidAt;
  private LocalDateTime settledAt;
  private List<AllocationDTO> allocations;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AllocationDTO {
    private Long groupId;
    private BigDecimal amount;
  }
}
