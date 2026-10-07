package com.splitz.expense.repository;

import com.splitz.expense.model.UserSimplificationPreference;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSimplificationPreferenceRepository
    extends JpaRepository<UserSimplificationPreference, Long> {

  Optional<UserSimplificationPreference> findByUserId(Long userId);

  /**
   * The subset of {@code userIds} that have opted out at the account level. A single query keeps
   * the effective opt-out resolution for a whole group to one round trip rather than one per
   * member.
   */
  @Query(
      "select p.userId from UserSimplificationPreference p where p.accountOptOut = true and p.userId in :userIds")
  Set<Long> findOptedOutUserIdsIn(@Param("userIds") Set<Long> userIds);
}
