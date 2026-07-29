package com.splitz.user.repository;

import com.splitz.user.model.OutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

  List<OutboxEvent> findTop50ByProcessedFalseOrderByCreatedAtAsc();
}
