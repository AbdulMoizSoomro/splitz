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
public class SplitRequest {

  private Long userId;
  private BigDecimal
      splitValue; // For EXACT, this is the amount. For EQUAL, it might be null or ignored.
}
