package com.splitz.user.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.event.FriendshipEvent;
import com.splitz.event.UserEvent;
import com.splitz.user.model.OutboxEvent;
import com.splitz.user.repository.OutboxRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDomainEventPublisher implements DomainEventPublisher {

  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  @Override
  public void publish(String aggregateType, String aggregateId, String eventType, Object payload) {
    if (outboxRepository == null) {
      return;
    }
    try {
      String eventId = extractOrGenerateEventId(payload);
      Instant timestamp = extractOrGenerateTimestamp(payload);
      String jsonPayload =
          (payload instanceof String) ? (String) payload : objectMapper.writeValueAsString(payload);

      OutboxEvent outboxEvent =
          OutboxEvent.builder()
              .id(eventId)
              .aggregateType(aggregateType)
              .aggregateId(aggregateId)
              .eventType(eventType)
              .payload(jsonPayload)
              .createdAt(timestamp)
              .processed(false)
              .build();

      outboxRepository.save(outboxEvent);
    } catch (JsonProcessingException e) {
      log.error(
          "Failed to serialize OutboxEvent payload for aggregate {} ({})",
          aggregateType,
          aggregateId,
          e);
      throw new RuntimeException(
          "Failed to serialize OutboxEvent payload for "
              + aggregateType.toLowerCase()
              + ": "
              + aggregateId,
          e);
    }
  }

  private String extractOrGenerateEventId(Object payload) {
    if (payload instanceof UserEvent ue && ue.getEventId() != null) {
      return ue.getEventId();
    }
    if (payload instanceof FriendshipEvent fe && fe.getEventId() != null) {
      return fe.getEventId();
    }
    return UUID.randomUUID().toString();
  }

  private Instant extractOrGenerateTimestamp(Object payload) {
    if (payload instanceof UserEvent ue && ue.getTimestamp() != null) {
      return ue.getTimestamp();
    }
    if (payload instanceof FriendshipEvent fe && fe.getTimestamp() != null) {
      return fe.getTimestamp();
    }
    return Instant.now();
  }
}
