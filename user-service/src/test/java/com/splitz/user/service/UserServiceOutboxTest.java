package com.splitz.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.user.dto.UserDTO;
import com.splitz.user.mapper.UserMapper;
import com.splitz.user.model.OutboxEvent;
import com.splitz.user.model.Role;
import com.splitz.user.model.User;
import com.splitz.user.repository.OutboxRepository;
import com.splitz.user.repository.RoleRepository;
import com.splitz.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Outbox Integration Unit Tests")
class UserServiceOutboxTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private OutboxRepository outboxRepository;
  @Mock private UserMapper userMapper;
  @Mock private BCryptPasswordEncoder passwordEncoder;

  @InjectMocks private UserService userService;

  @Test
  @DisplayName("Should save USER_CREATED OutboxEvent when user is created")
  void testCreateUser_SavesOutboxEvent() {
    UserDTO inputDto = new UserDTO();
    inputDto.setUsername("alice");
    inputDto.setEmail("alice@example.com");
    inputDto.setFirstName("Alice");
    inputDto.setLastName("Smith");

    User entity = new User();
    entity.setId(100L);
    entity.setUsername("alice");
    entity.setEmail("alice@example.com");
    entity.setFirstName("Alice");
    entity.setLastName("Smith");

    Role role = new Role();
    role.setName("ROLE_USER");

    when(userRepository.findByusername("alice")).thenReturn(Optional.empty());
    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
    when(userMapper.toEntityWithPasswordEncoding(any(), any())).thenReturn(entity);
    when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));
    when(userRepository.save(any(User.class))).thenReturn(entity);
    when(userMapper.toDTO(entity)).thenReturn(inputDto);

    userService.createUser(inputDto);

    ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(outboxCaptor.capture());

    OutboxEvent savedEvent = outboxCaptor.getValue();
    assertThat(savedEvent).isNotNull();
    assertThat(savedEvent.getAggregateType()).isEqualTo("USER");
    assertThat(savedEvent.getAggregateId()).isEqualTo("100");
    assertThat(savedEvent.getEventType()).isEqualTo("USER_CREATED");
    assertThat(savedEvent.getPayload()).contains("alice@example.com");
    assertThat(savedEvent.isProcessed()).isFalse();
  }
}
