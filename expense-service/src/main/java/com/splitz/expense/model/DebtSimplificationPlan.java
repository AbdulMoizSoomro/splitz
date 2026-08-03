package com.splitz.expense.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "debt_simplification_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtSimplificationPlan {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "group_id", nullable = false)
  private Long groupId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PlanStatus status;

  @Column(name = "original_transaction_count", nullable = false)
  private int originalTransactionCount;

  @Column(name = "simplified_transaction_count", nullable = false)
  private int simplifiedTransactionCount;

  @Column(name = "total_debt_volume", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalDebtVolume;

  @ElementCollection
  @CollectionTable(name = "plan_opt_out_users", joinColumns = @JoinColumn(name = "plan_id"))
  @Column(name = "user_id")
  @Builder.Default
  private Set<Long> optedOutUserIds = new HashSet<>();

  @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<SimplifiedDebtTransaction> transactions = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;
}
