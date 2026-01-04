package com.splitz.expense.dto;

import com.splitz.expense.model.GroupRole;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberDTO {
  private Long userId;
  private GroupRole role;
  private LocalDateTime joinedAt;
}
