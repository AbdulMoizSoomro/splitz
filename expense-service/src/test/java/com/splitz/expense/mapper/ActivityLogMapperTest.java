package com.splitz.expense.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.splitz.expense.dto.ActivityLogDTO;
import com.splitz.expense.model.ActivityLog;
import com.splitz.expense.model.ActivityLogType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityLogMapperTest {

  private final ActivityLogMapper mapper = new ActivityLogMapper();

  @Test
  void toDTO_MapsEveryField() {
    LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 30);
    ActivityLog log =
        ActivityLog.builder()
            .id(1L)
            .groupId(2L)
            .type(ActivityLogType.EXPENSE_UPDATED)
            .actorId(3L)
            .entityId(4L)
            .entityName("Dinner")
            .timestamp(timestamp)
            .details("amount: 60.00 -> 100.00;")
            .build();

    ActivityLogDTO dto = mapper.toDTO(log);

    assertEquals(1L, dto.getId());
    assertEquals(2L, dto.getGroupId());
    assertEquals(ActivityLogType.EXPENSE_UPDATED, dto.getType());
    assertEquals(3L, dto.getActorId());
    assertEquals(4L, dto.getEntityId());
    assertEquals("Dinner", dto.getEntityName());
    assertEquals(timestamp, dto.getTimestamp());
    assertEquals("amount: 60.00 -> 100.00;", dto.getDetails());
  }

  @Test
  void toDTO_Null_ReturnsNull() {
    assertNull(mapper.toDTO(null));
  }

  @Test
  void toDTOList_MapsEachElementInOrder() {
    ActivityLog first =
        ActivityLog.builder()
            .id(1L)
            .groupId(2L)
            .type(ActivityLogType.EXPENSE_CREATED)
            .actorId(3L)
            .entityName("Dinner")
            .build();
    ActivityLog second =
        ActivityLog.builder()
            .id(10L)
            .groupId(2L)
            .type(ActivityLogType.EXPENSE_DELETED)
            .actorId(5L)
            .entityName("Lunch")
            .build();

    List<ActivityLogDTO> dtos = mapper.toDTOList(List.of(first, second));

    assertEquals(2, dtos.size());
    assertEquals(1L, dtos.get(0).getId());
    assertEquals(10L, dtos.get(1).getId());
  }

  @Test
  void toDTOList_Null_ReturnsNull() {
    assertNull(mapper.toDTOList(null));
  }
}
