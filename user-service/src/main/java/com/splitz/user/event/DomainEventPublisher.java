package com.splitz.user.event;

public interface DomainEventPublisher {

  void publish(String aggregateType, String aggregateId, String eventType, Object payload);
}
