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
public class FriendGroupBalanceDTO {
  private Long groupId;
  private String groupName;
  private BigDecimal balance;
}
