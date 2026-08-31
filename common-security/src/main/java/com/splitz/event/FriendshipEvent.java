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
public class FriendshipEvent implements DomainEvent {

  private static final long serialVersionUID = 1L;

  private String eventId;
  private String eventType;
  private Long userId;
  private Long friendId;
  private String status;
  private Instant timestamp;
}
