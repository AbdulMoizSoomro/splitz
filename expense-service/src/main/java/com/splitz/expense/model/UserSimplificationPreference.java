package com.splitz.expense.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Account-level debt simplification preference. An {@code accountOptOut} is a hard override: while
 * set, the user is excluded from debt netting in every group they belong to, including groups whose
 * per-group settings predate the opt-out.
 *
 * <p>This exists because the account-level opt-out promised by issue #67 ("users can opt out of
 * transitive debt transfers globally") was never persisted; group-scoped opt-out alone could not
 * honour consent the user gave after the group was configured.
 */
@Entity
@Table(name = "user_simplification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSimplificationPreference {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, unique = true)
  private Long userId;

  @Builder.Default
  @Column(name = "account_opt_out", nullable = false)
  private boolean accountOptOut = false;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;

  /** The canonical preference for a user with no persisted row: opted in everywhere. */
  public static UserSimplificationPreference defaults(Long userId) {
    return UserSimplificationPreference.builder().userId(userId).accountOptOut(false).build();
  }
}
