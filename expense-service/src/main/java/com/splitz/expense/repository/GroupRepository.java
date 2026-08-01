package com.splitz.expense.repository;

import com.splitz.expense.model.Group;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

public interface GroupRepository extends JpaRepository<Group, Long> {

  // Fetch ALL members for each group (left join fetch) while filtering the groups
  // to those the user belongs to via a subquery. Previously this used a derived
  // query name (MembersUserId) together with @EntityGraph(attributePaths =
  // "members"), which made Hibernate generate a fetch join on `members` *with a
  // WHERE clause on the member's userId*. That filters the loaded `members`
  // collection to only the current user's membership, so callers like the friend
  // detail page and the expense modal only ever saw themselves as a member.
  // Moving the membership test into a subquery keeps it separate from the
  // collection fetch, so every member is loaded correctly in a single query.
  @Query(
      "select distinct g from Group g "
          + "left join fetch g.members "
          + "where g.active = true "
          + "and g.id in (select gm.group.id from GroupMember gm where gm.userId = :userId)")
  List<Group> findDistinctByMembersUserIdAndActiveTrue(@Param("userId") Long userId);

  @Override
  @NonNull
  @EntityGraph(attributePaths = "members")
  Optional<Group> findById(@NonNull Long id);
}
