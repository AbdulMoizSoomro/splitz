package com.splitz.expense.repository;

import com.splitz.expense.model.Group;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

  @EntityGraph(attributePaths = "members")
  List<Group> findDistinctByMembersUserIdAndActiveTrue(Long userId);

  @Override
  @EntityGraph(attributePaths = "members")
  Optional<Group> findById(Long id);
}
