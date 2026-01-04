package com.splitz.user.repository;

import com.splitz.user.model.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByusername(String username);

  Optional<User> findByEmail(String email);

  @Query(
      "SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) "
          + "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) "
          + "OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) "
          + "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%'))")
  Page<User> searchByUsernameOrEmailOrFirstName(@Param("query") String query, Pageable pageable);
}
