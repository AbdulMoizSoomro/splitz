package com.splitz.user.repository;

import com.splitz.user.model.OutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

  List<OutboxEvent> findTop50ByProcessedFalseOrderByCreatedAtAsc();
}
