package com.splitz.expense.repository;

import com.splitz.expense.model.Settlement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByGroupId(Long groupId);

    List<Settlement> findByPayerIdOrPayeeId(Long payerId, Long payeeId);
}
