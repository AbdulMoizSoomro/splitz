package com.splitz.expense.client;

import com.splitz.expense.dto.UserResponse;
import java.util.Optional;

public interface UserClient {

  Optional<UserResponse> getUserById(Long id);

  boolean existsById(Long id);
}
