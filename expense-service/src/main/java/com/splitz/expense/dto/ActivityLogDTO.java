package com.splitz.expense.dto;

import com.splitz.expense.model.ActivityLogType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLogDTO {
  private Long id;
  private Long groupId;
  private ActivityLogType type;
  private Long actorId;
  private Long entityId;
  private String entityName;
  private LocalDateTime timestamp;
  private String details;
}
