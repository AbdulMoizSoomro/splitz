package com.splitz.expense.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalActivityResponseDTO {
  private List<ExpenseDTO> expenses;
  private List<SettlementDTO> settlements;
}
