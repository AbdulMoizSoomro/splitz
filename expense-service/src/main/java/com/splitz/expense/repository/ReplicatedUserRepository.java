package com.splitz.expense.repository;

import com.splitz.expense.model.ReplicatedUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplicatedUserRepository extends JpaRepository<ReplicatedUser, Long> {}
