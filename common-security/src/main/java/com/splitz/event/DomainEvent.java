package com.splitz.event;

import java.io.Serializable;
import java.time.Instant;

public interface DomainEvent extends Serializable {

  String getEventId();

  void setEventId(String eventId);

  String getEventType();

  void setEventType(String eventType);

  Instant getTimestamp();

  void setTimestamp(Instant timestamp);
}
