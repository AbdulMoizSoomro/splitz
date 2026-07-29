package com.splitz.user.service;

import com.splitz.user.config.RabbitMQConfig;
import com.splitz.user.model.OutboxEvent;
import com.splitz.user.repository.OutboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

  private final OutboxRepository outboxRepository;
  private final RabbitTemplate rabbitTemplate;

  @Scheduled(fixedDelay = 2000)
  @Transactional
  public void publishPendingEvents() {
    List<OutboxEvent> pendingEvents =
        outboxRepository.findTop50ByProcessedFalseOrderByCreatedAtAsc();
    if (pendingEvents.isEmpty()) {
      return;
    }

    log.info("Found {} pending outbox events to publish", pendingEvents.size());

    for (OutboxEvent event : pendingEvents) {
      try {
        String routingKey = determineRoutingKey(event);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, event.getPayload());
        event.setProcessed(true);
      } catch (Exception e) {
        log.error(
            "Failed to publish outbox event: id={}, type={}",
            event.getId(),
            event.getEventType(),
            e);
      }
    }

    outboxRepository.saveAll(pendingEvents);
  }

  private String determineRoutingKey(OutboxEvent event) {
    String aggregate =
        event.getAggregateType() != null ? event.getAggregateType().toLowerCase() : "unknown";
    String eventType =
        event.getEventType() != null
            ? event.getEventType().toLowerCase().replace("user_", "").replace("friendship_", "")
            : "updated";
    return aggregate + "." + eventType;
  }
}
