package com.splitz.expense.repository;

import com.splitz.expense.model.ActivityLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
  List<ActivityLog> findByGroupIdOrderByTimestampDesc(Long groupId);
}
