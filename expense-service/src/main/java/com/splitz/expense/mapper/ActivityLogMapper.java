package com.splitz.expense.mapper;

import com.splitz.expense.dto.ActivityLogDTO;
import com.splitz.expense.model.ActivityLog;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ActivityLogMapper {

  public ActivityLogDTO toDTO(ActivityLog log) {
    if (log == null) return null;
    return ActivityLogDTO.builder()
        .id(log.getId())
        .groupId(log.getGroupId())
        .type(log.getType())
        .actorId(log.getActorId())
        .entityId(log.getEntityId())
        .entityName(log.getEntityName())
        .timestamp(log.getTimestamp())
        .details(log.getDetails())
        .build();
  }

  public List<ActivityLogDTO> toDTOList(List<ActivityLog> logs) {
    if (logs == null) return null;
    return logs.stream().map(this::toDTO).collect(Collectors.toList());
  }
}
