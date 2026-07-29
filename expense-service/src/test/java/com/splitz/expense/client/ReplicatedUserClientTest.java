package com.splitz.expense.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.model.ReplicatedUser;
import com.splitz.expense.repository.ReplicatedUserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReplicatedUserClient Unit Tests")
class ReplicatedUserClientTest {

  @Mock private ReplicatedUserRepository replicatedUserRepository;
  @Mock private WebClientUserClient fallbackUserClient;

  @InjectMocks private ReplicatedUserClient replicatedUserClient;

  @Test
  @DisplayName("Should return user from local replicated table if present")
  void testGetUserById_WhenLocalCacheHit_ReturnsLocalUser() {
    ReplicatedUser localUser =
        ReplicatedUser.builder()
            .userId(42L)
            .username("johndoe")
            .fullName("John Doe")
            .email("john@example.com")
            .updatedAt(Instant.now())
            .build();

    when(replicatedUserRepository.findById(42L)).thenReturn(Optional.of(localUser));

    Optional<UserResponse> result = replicatedUserClient.getUserById(42L);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(42L);
    assertThat(result.get().getUsername()).isEqualTo("johndoe");
    verifyNoInteractions(fallbackUserClient);
  }

  @Test
  @DisplayName("Should fall back to WebClientUserClient on local miss and write through")
  void testGetUserById_WhenLocalCacheMiss_FetchesAndStoresLocally() {
    when(replicatedUserRepository.findById(99L)).thenReturn(Optional.empty());

    UserResponse remoteUser = new UserResponse();
    remoteUser.setId(99L);
    remoteUser.setUsername("remoteuser");
    remoteUser.setFirstName("Remote");
    remoteUser.setLastName("User");
    remoteUser.setEmail("remote@example.com");

    when(fallbackUserClient.getUserById(99L)).thenReturn(Optional.of(remoteUser));

    Optional<UserResponse> result = replicatedUserClient.getUserById(99L);

    assertThat(result).isPresent();
    assertThat(result.get().getUsername()).isEqualTo("remoteuser");
    verify(fallbackUserClient).getUserById(99L);
    verify(replicatedUserRepository).save(org.mockito.ArgumentMatchers.any(ReplicatedUser.class));
  }
}
