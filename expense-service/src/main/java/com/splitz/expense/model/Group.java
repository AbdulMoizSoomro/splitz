package com.splitz.expense.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
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
@Table(name = "groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "image_url")
  private String imageUrl;

  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Builder.Default
  @Column(name = "allow_members_to_manage_members", nullable = false)
  private boolean allowMembersToManageMembers = true;

  @Builder.Default
  @Column(name = "allow_members_to_edit_expenses", nullable = false)
  private boolean allowMembersToEditExpenses = true;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Builder.Default
  @OneToMany(
      mappedBy = "group",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private Set<GroupMember> members = new HashSet<>();

  public void addMember(GroupMember member) {
    members.add(member);
    member.setGroup(this);
  }

  public void removeMember(GroupMember member) {
    members.remove(member);
    member.setGroup(null);
  }
}
