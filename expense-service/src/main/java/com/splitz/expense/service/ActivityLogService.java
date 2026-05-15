package com.splitz.expense.service;

import com.splitz.expense.model.ActivityLog;
import com.splitz.expense.model.ActivityLogType;
import java.util.List;

public interface ActivityLogService {
  void logActivity(
      Long groupId,
      ActivityLogType type,
      Long actorId,
      Long entityId,
      String entityName,
      String details);

  List<ActivityLog> getActivitiesByGroup(Long groupId);
}
