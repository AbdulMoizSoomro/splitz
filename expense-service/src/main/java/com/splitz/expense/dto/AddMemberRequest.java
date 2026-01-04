package com.splitz.expense.dto;

import com.splitz.expense.model.GroupRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddMemberRequest {

  @NotNull(message = "userId is required")
  private Long userId;

  private GroupRole role;
}
