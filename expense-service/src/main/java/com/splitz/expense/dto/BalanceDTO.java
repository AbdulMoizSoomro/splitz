package com.splitz.expense.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceDTO {

  private Long userId;
  private String username;
  private String email;
  private String firstName;
  private String lastName;
  private BigDecimal balance;
}
