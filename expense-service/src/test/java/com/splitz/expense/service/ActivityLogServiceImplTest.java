package com.splitz.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.dto.ActivityLogDTO;
import com.splitz.expense.mapper.ActivityLogMapper;
import com.splitz.expense.model.ActivityLog;
import com.splitz.expense.model.ActivityLogType;
import com.splitz.expense.repository.ActivityLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityLogServiceImplTest {

  @Mock private ActivityLogRepository activityLogRepository;

  @Mock private ActivityLogMapper activityLogMapper;

  @InjectMocks private ActivityLogServiceImpl activityLogService;

  @Test
  void logActivity_PersistsTheActivityRecord() {
    activityLogService.logActivity(
        2L, ActivityLogType.EXPENSE_CREATED, 3L, 4L, "Dinner", "description: X -> Y;");

    verify(activityLogRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                log ->
                    log.getGroupId() == 2L
                        && log.getType() == ActivityLogType.EXPENSE_CREATED
                        && log.getActorId() == 3L
                        && log.getEntityId() == 4L
                        && "Dinner".equals(log.getEntityName())
                        && "description: X -> Y;".equals(log.getDetails())));
  }

  @Test
  void getActivitiesByGroup_ReturnsDtosThroughMapper() {
    ActivityLog log =
        ActivityLog.builder()
            .id(1L)
            .groupId(2L)
            .type(ActivityLogType.EXPENSE_CREATED)
            .actorId(3L)
            .entityId(4L)
            .entityName("Dinner")
            .timestamp(LocalDateTime.now())
            .build();
    ActivityLogDTO dto =
        ActivityLogDTO.builder()
            .id(1L)
            .groupId(2L)
            .type(ActivityLogType.EXPENSE_CREATED)
            .actorId(3L)
            .entityId(4L)
            .entityName("Dinner")
            .build();

    when(activityLogRepository.findByGroupIdOrderByTimestampDesc(2L)).thenReturn(List.of(log));
    when(activityLogMapper.toDTOList(List.of(log))).thenReturn(List.of(dto));

    List<ActivityLogDTO> result = activityLogService.getActivitiesByGroup(2L);

    assertEquals(List.of(dto), result);
    verify(activityLogMapper).toDTOList(eq(List.of(log)));
    verify(activityLogRepository).findByGroupIdOrderByTimestampDesc(2L);
  }
}
