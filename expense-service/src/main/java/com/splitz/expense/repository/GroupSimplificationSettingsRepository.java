package com.splitz.expense.repository;

import com.splitz.expense.model.GroupSimplificationSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupSimplificationSettingsRepository
    extends JpaRepository<GroupSimplificationSettings, Long> {

  Optional<GroupSimplificationSettings> findByGroupId(Long groupId);
}
