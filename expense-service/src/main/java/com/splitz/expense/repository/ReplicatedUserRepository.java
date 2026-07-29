package com.splitz.expense.repository;

import com.splitz.expense.model.ReplicatedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReplicatedUserRepository extends JpaRepository<ReplicatedUser, Long> {}
