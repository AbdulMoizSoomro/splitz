package com.splitz.expense.dto;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupSimplificationSettingsDTO {
  private Long groupId;
  private boolean simplificationEnabled;
  private String simplificationScope;
  private Set<Long> optOutUserIds;
}
