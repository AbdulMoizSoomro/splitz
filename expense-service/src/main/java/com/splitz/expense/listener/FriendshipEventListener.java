package com.splitz.expense.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.event.FriendshipEvent;
import com.splitz.expense.config.RabbitMQConfig;
import com.splitz.expense.model.ProcessedEvent;
import com.splitz.expense.model.ReplicatedFriendship;
import com.splitz.expense.repository.ProcessedEventRepository;
import com.splitz.expense.repository.ReplicatedFriendshipRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendshipEventListener {

  private final ReplicatedFriendshipRepository replicatedFriendshipRepository;
  private final ProcessedEventRepository processedEventRepository;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitMQConfig.FRIENDSHIP_QUEUE_NAME)
  @Transactional
  public void handleFriendshipEvent(String messagePayload) {
    try {
      FriendshipEvent event = objectMapper.readValue(messagePayload, FriendshipEvent.class);
      if (event.getEventId() == null || processedEventRepository.existsById(event.getEventId())) {
        log.debug("FriendshipEvent already processed or missing eventId: {}", event.getEventId());
        return;
      }

      log.info(
          "Processing FriendshipEvent: id={}, type={}, user1={}, user2={}",
          event.getEventId(),
          event.getEventType(),
          event.getUserId(),
          event.getFriendId());

      String relationshipKey = buildRelationshipKey(event.getUserId(), event.getFriendId());

      if ("FRIENDSHIP_REMOVED".equalsIgnoreCase(event.getEventType())
          || "REMOVED".equalsIgnoreCase(event.getStatus())) {
        replicatedFriendshipRepository.deleteById(relationshipKey);
      } else {
        ReplicatedFriendship friendship =
            ReplicatedFriendship.builder()
                .id(relationshipKey)
                .userId(event.getUserId())
                .friendId(event.getFriendId())
                .status(event.getStatus() != null ? event.getStatus() : "PENDING")
                .updatedAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .build();

        replicatedFriendshipRepository.save(friendship);
      }

      ProcessedEvent processedEvent =
          ProcessedEvent.builder().eventId(event.getEventId()).processedAt(Instant.now()).build();

      processedEventRepository.save(processedEvent);

    } catch (Exception e) {
      log.error("Failed to process FriendshipEvent payload: {}", messagePayload, e);
      throw new RuntimeException("FriendshipEvent processing error", e);
    }
  }

  private String buildRelationshipKey(Long u1, Long u2) {
    long first = Math.min(u1, u2);
    long second = Math.max(u1, u2);
    return first + "_" + second;
  }
}
