package com.splitz.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSettlementRequest {

  private Long groupId;

  @NotNull(message = "Payer ID is required")
  private Long payerId;

  @NotNull(message = "Payee ID is required")
  private Long payeeId;

  @NotNull(message = "Amount is required")
  @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
  private BigDecimal amount;

  private java.util.List<Allocation> allocations;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Allocation {
    @NotNull(message = "Allocation Group ID is required")
    private Long groupId;

    @NotNull(message = "Allocation amount is required")
    @DecimalMin(value = "0.01", message = "Allocation amount must be greater than 0")
    private BigDecimal amount;
  }
}
