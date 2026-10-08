package com.splitz.expense.dto;

import com.splitz.expense.model.SplitType;
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

  /** How the expense was split, so a read can restore the original split inputs. */
  private SplitType splitType;

  /**
   * The per-member input the split was computed from: an amount for {@code EXACT}, a percentage for
   * {@code PERCENTAGE}, a share count for {@code SHARES}, a delta for {@code ADJUSTMENT}, and null
   * for {@code EQUAL}, which has no per-member input.
   */
  private BigDecimal splitValue;

  private BigDecimal shareAmount;
}
