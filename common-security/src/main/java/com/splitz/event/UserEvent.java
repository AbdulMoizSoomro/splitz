package com.splitz.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent implements DomainEvent {

  private static final long serialVersionUID = 1L;

  private String eventId;
  private String eventType;
  private Long userId;
  private String username;
  private String fullName;
  private String email;
  private Instant timestamp;
}
