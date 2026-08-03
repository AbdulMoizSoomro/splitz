package com.splitz.expense.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "group_simplification_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupSimplificationSettings {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "group_id", nullable = false, unique = true)
  private Long groupId;

  @Builder.Default
  @Column(name = "simplification_enabled", nullable = false)
  private boolean simplificationEnabled = true;

  @Enumerated(EnumType.STRING)
  @Column(name = "simplification_scope", nullable = false)
  @Builder.Default
  private SimplificationScope simplificationScope = SimplificationScope.INTRA_GROUP;

  @ElementCollection
  @CollectionTable(
      name = "group_simplification_opt_outs",
      joinColumns = @JoinColumn(name = "settings_id"))
  @Column(name = "user_id")
  @Builder.Default
  private Set<Long> optOutUserIds = new HashSet<>();

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;
}
