package com.splitz.expense.client;

import com.splitz.expense.dto.UserResponse;
import java.util.List;
import java.util.Optional;

public interface UserClient {

  Optional<UserResponse> getUserById(Long id);

  List<UserResponse> getUsersByIds(List<Long> ids);

  boolean existsById(Long id);
}
