package com.splitz.expense.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseSplitDTO {

  private Long id;
  private Long userId;
  private BigDecimal shareAmount;
}
