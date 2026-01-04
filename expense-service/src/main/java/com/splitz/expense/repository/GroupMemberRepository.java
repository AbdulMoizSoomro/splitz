package com.splitz.expense.repository;

import com.splitz.expense.model.GroupMember;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

  boolean existsByGroupIdAndUserId(Long groupId, Long userId);

  Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);
}
