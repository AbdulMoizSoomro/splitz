package com.splitz.expense.client;

import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.model.ReplicatedUser;
import com.splitz.expense.repository.ReplicatedUserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class ReplicatedUserClient implements UserClient {

  private final ReplicatedUserRepository replicatedUserRepository;
  private final WebClientUserClient fallbackUserClient;

  @Override
  public Optional<UserResponse> getUserById(Long id) {
    if (id == null) {
      return Optional.empty();
    }

    Optional<ReplicatedUser> local = replicatedUserRepository.findById(id);
    if (local.isPresent()) {
      log.debug("Cache hit in replicated_users for id: {}", id);
      return Optional.of(toUserResponse(local.get()));
    }

    log.info("Cache miss in replicated_users for id: {}, delegating to WebClientUserClient", id);
    Optional<UserResponse> remote = fallbackUserClient.getUserById(id);
    remote.ifPresent(this::saveLocalReplica);

    return remote;
  }

  @Override
  public List<UserResponse> getUsersByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }

    List<UserResponse> results = new ArrayList<>();
    List<Long> missingIds = new ArrayList<>();

    for (Long id : ids) {
      Optional<ReplicatedUser> local = replicatedUserRepository.findById(id);
      if (local.isPresent()) {
        results.add(toUserResponse(local.get()));
      } else {
        missingIds.add(id);
      }
    }

    if (!missingIds.isEmpty()) {
      log.info("Cache miss in replicated_users for ids: {}, fetching remotely", missingIds);
      List<UserResponse> remoteUsers = fallbackUserClient.getUsersByIds(missingIds);
      for (UserResponse remote : remoteUsers) {
        saveLocalReplica(remote);
        results.add(remote);
      }
    }

    return results;
  }

  @Override
  public List<UserResponse> getFriends(Long userId) {
    return fallbackUserClient.getFriends(userId);
  }

  @Override
  public boolean existsById(Long id) {
    if (id == null) {
      return false;
    }
    return replicatedUserRepository.existsById(id) || fallbackUserClient.existsById(id);
  }

  private void saveLocalReplica(UserResponse response) {
    try {
      String fullName =
          (response.getFirstName() != null ? response.getFirstName() : "")
              + " "
              + (response.getLastName() != null ? response.getLastName() : "");
      ReplicatedUser replica =
          ReplicatedUser.builder()
              .userId(response.getId())
              .username(response.getUsername())
              .fullName(fullName.trim())
              .email(response.getEmail())
              .updatedAt(Instant.now())
              .build();
      replicatedUserRepository.save(replica);
    } catch (Exception e) {
      log.warn("Failed to write through local replica for user: {}", response.getId(), e);
    }
  }

  private UserResponse toUserResponse(ReplicatedUser local) {
    UserResponse dto = new UserResponse();
    dto.setId(local.getUserId());
    dto.setUsername(local.getUsername());
    if (local.getFullName() != null) {
      String[] parts = local.getFullName().split(" ", 2);
      dto.setFirstName(parts[0]);
      if (parts.length > 1) {
        dto.setLastName(parts[1]);
      }
    }
    dto.setEmail(local.getEmail());
    return dto;
  }
}
