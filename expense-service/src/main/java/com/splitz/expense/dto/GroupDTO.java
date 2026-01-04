package com.splitz.expense.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupDTO {
  private Long id;
  private String name;
  private String description;
  private String imageUrl;
  private Long createdBy;
  private boolean active;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private List<GroupMemberDTO> members;
}
