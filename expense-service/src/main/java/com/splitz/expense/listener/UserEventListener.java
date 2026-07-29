package com.splitz.expense.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.event.UserEvent;
import com.splitz.expense.config.RabbitMQConfig;
import com.splitz.expense.model.ProcessedEvent;
import com.splitz.expense.model.ReplicatedUser;
import com.splitz.expense.repository.ProcessedEventRepository;
import com.splitz.expense.repository.ReplicatedUserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

  private final ReplicatedUserRepository replicatedUserRepository;
  private final ProcessedEventRepository processedEventRepository;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitMQConfig.USER_QUEUE_NAME)
  @Transactional
  public void handleUserEvent(String messagePayload) {
    try {
      UserEvent event = objectMapper.readValue(messagePayload, UserEvent.class);
      if (event.getEventId() == null || processedEventRepository.existsById(event.getEventId())) {
        log.debug("UserEvent already processed or missing eventId: {}", event.getEventId());
        return;
      }

      log.info(
          "Processing UserEvent: id={}, type={}, userId={}",
          event.getEventId(),
          event.getEventType(),
          event.getUserId());

      if ("USER_DELETED".equalsIgnoreCase(event.getEventType())) {
        replicatedUserRepository.deleteById(event.getUserId());
      } else {
        ReplicatedUser user =
            ReplicatedUser.builder()
                .userId(event.getUserId())
                .username(event.getUsername())
                .fullName(event.getFullName())
                .email(event.getEmail())
                .updatedAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .build();

        replicatedUserRepository.save(user);
      }

      ProcessedEvent processedEvent =
          ProcessedEvent.builder().eventId(event.getEventId()).processedAt(Instant.now()).build();

      processedEventRepository.save(processedEvent);

    } catch (Exception e) {
      log.error("Failed to process UserEvent payload: {}", messagePayload, e);
      throw new RuntimeException("UserEvent processing error", e);
    }
  }
}
