package com.splitz.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.event.UserEvent;
import com.splitz.user.dto.UserDTO;
import com.splitz.user.event.DomainEventPublisher;
import com.splitz.user.mapper.UserMapper;
import com.splitz.user.model.Role;
import com.splitz.user.model.User;
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
@DisplayName("UserService Domain Event Unit Tests")
class UserServiceOutboxTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private UserMapper userMapper;
  @Mock private BCryptPasswordEncoder passwordEncoder;

  @InjectMocks private UserService userService;

  @Test
  @DisplayName("Should publish USER_CREATED event when user is created")
  void testCreateUser_PublishesEvent() {
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

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher)
        .publish(eq("USER"), eq("100"), eq("USER_CREATED"), payloadCaptor.capture());

    Object payload = payloadCaptor.getValue();
    assertThat(payload).isInstanceOf(UserEvent.class);
    UserEvent userEvent = (UserEvent) payload;
    assertThat(userEvent.getUserId()).isEqualTo(100L);
    assertThat(userEvent.getUsername()).isEqualTo("alice");
    assertThat(userEvent.getEmail()).isEqualTo("alice@example.com");
    assertThat(userEvent.getFullName()).isEqualTo("Alice Smith");
    assertThat(userEvent.getEventType()).isEqualTo("USER_CREATED");
  }
}
