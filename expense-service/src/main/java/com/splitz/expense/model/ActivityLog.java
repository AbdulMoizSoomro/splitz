package com.splitz.expense.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "group_id", nullable = false)
  private Long groupId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ActivityLogType type;

  @Column(name = "actor_id", nullable = false)
  private Long actorId;

  @Column(name = "entity_id")
  private Long entityId;

  @Column(name = "entity_name")
  private String entityName;

  @Column(nullable = false)
  private LocalDateTime timestamp;

  @Column(columnDefinition = "TEXT")
  private String details;

  @PrePersist
  protected void onCreate() {
    if (timestamp == null) {
      timestamp = LocalDateTime.now();
    }
  }
}
