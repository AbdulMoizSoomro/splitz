package com.splitz.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The authenticated user's account-level debt simplification preference. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSimplificationPreferenceDTO {
  private Long userId;
  private boolean accountOptOut;
}
