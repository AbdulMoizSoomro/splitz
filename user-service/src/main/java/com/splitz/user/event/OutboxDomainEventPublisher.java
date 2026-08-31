package com.splitz.user.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.event.DomainEvent;
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

      if (payload instanceof DomainEvent de) {
        if (de.getEventId() == null) {
          de.setEventId(eventId);
        }
        if (de.getEventType() == null) {
          de.setEventType(eventType);
        }
        if (de.getTimestamp() == null) {
          de.setTimestamp(timestamp);
        }
      }

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
    if (payload instanceof DomainEvent de && de.getEventId() != null) {
      return de.getEventId();
    }
    return UUID.randomUUID().toString();
  }

  private Instant extractOrGenerateTimestamp(Object payload) {
    if (payload instanceof DomainEvent de && de.getTimestamp() != null) {
      return de.getTimestamp();
    }
    return Instant.now();
  }
}
