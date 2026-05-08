package com.splitz.expense.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class BulkAddMembersRequest {

  @NotEmpty(message = "userIds must not be empty")
  private List<Long> userIds;
}
