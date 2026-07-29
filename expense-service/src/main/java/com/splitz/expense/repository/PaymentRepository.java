package com.splitz.expense.repository;

import com.splitz.expense.model.Payment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Payment p WHERE p.id = :id")
  Optional<Payment> findByIdWithLock(@Param("id") Long id);

  List<Payment> findByPayerIdOrPayeeId(Long payerId, Long payeeId);

  @Query(
      "SELECT p FROM Payment p WHERE (p.payerId = :userId1 AND p.payeeId = :userId2) OR (p.payerId = :userId2 AND p.payeeId = :userId1)")
  List<Payment> findBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
