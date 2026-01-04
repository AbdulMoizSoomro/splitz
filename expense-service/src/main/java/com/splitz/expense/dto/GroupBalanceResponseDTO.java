package com.splitz.expense.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupBalanceResponseDTO {

  private Long groupId;
  private List<BalanceDTO> balances;
  private List<DebtDTO> simplifiedDebts;
}
