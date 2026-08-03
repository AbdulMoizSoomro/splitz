package com.splitz.expense.service;

import com.splitz.expense.dto.ActivityLogDTO;
import com.splitz.expense.mapper.ActivityLogMapper;
import com.splitz.expense.model.ActivityLog;
import com.splitz.expense.model.ActivityLogType;
import com.splitz.expense.repository.ActivityLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityLogServiceImpl implements ActivityLogService {

  private final ActivityLogRepository activityLogRepository;
  private final ActivityLogMapper activityLogMapper;

  @Override
  @Transactional
  public void logActivity(
      Long groupId,
      ActivityLogType type,
      Long actorId,
      Long entityId,
      String entityName,
      String details) {
    ActivityLog log =
        ActivityLog.builder()
            .groupId(groupId)
            .type(type)
            .actorId(actorId)
            .entityId(entityId)
            .entityName(entityName)
            .details(details)
            .build();
    activityLogRepository.save(log);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActivityLogDTO> getActivitiesByGroup(Long groupId) {
    return activityLogMapper.toDTOList(
        activityLogRepository.findByGroupIdOrderByTimestampDesc(groupId));
  }
}
