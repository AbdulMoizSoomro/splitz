package com.splitz.expense.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A read-only Suggested Settlement Plan produced by the Smart Debt Reduction Engine (Wayfinder
 * issue #63). Computed on demand from live group balances and governance opt-outs; never mutates
 * balances.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtSimplificationPlanDTO {
  private Long groupId;
  private String scope;
  private String status;
  private boolean simplificationEnabled;
  private int originalTransactionCount;
  private int simplifiedTransactionCount;
  private BigDecimal totalDebtVolume;
  private Set<Long> optedOutUserIds;
  private List<SimplifiedDebtTransactionDTO> transactions;
}
