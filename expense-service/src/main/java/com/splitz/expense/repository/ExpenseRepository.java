package com.splitz.expense.repository;

import com.splitz.expense.model.Expense;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

  List<Expense> findByGroupId(Long groupId);
}
