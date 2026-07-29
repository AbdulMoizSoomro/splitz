package com.splitz.expense.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "replicated_friendships")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplicatedFriendship {

  @Id
  @Column(name = "id", length = 100)
  private String id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "friend_id", nullable = false)
  private Long friendId;

  @Column(name = "status", nullable = false, length = 50)
  private String status;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
