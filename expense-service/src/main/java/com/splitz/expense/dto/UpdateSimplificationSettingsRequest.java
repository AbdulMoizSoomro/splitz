package com.splitz.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSimplificationSettingsRequest {
  private boolean simplificationEnabled;
}
