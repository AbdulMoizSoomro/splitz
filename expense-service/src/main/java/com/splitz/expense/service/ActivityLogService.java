package com.splitz.expense.service;

import com.splitz.expense.dto.ActivityLogDTO;
import com.splitz.expense.model.ActivityLogType;
import java.util.List;

/** Seam for reading and writing the group activity feed. */
public interface ActivityLogService {
  void logActivity(
      Long groupId,
      ActivityLogType type,
      Long actorId,
      Long entityId,
      String entityName,
      String details);

  List<ActivityLogDTO> getActivitiesByGroup(Long groupId);
}
