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
@Table(name = "replicated_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplicatedUser {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "username", nullable = false, length = 100)
  private String username;

  @Column(name = "full_name", length = 200)
  private String fullName;

  @Column(name = "email", length = 200)
  private String email;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
