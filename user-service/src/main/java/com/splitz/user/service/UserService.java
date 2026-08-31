package com.splitz.user.service;

import com.splitz.event.UserEvent;
import com.splitz.user.dto.UpdateUserDTO;
import com.splitz.user.dto.UserDTO;
import com.splitz.user.event.DomainEventPublisher;
import com.splitz.user.exception.ResourceNotFoundException;
import com.splitz.user.exception.UserAlreadyExistsException;
import com.splitz.user.mapper.UserMapper;
import com.splitz.user.model.Role;
import com.splitz.user.model.User;
import com.splitz.user.repository.RoleRepository;
import com.splitz.user.repository.UserRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final DomainEventPublisher eventPublisher;
  private final UserMapper userMapper;
  private final BCryptPasswordEncoder passwordEncoder;

  public Page<UserDTO> getAllUsers(Pageable pageable) {
    return userRepository.findAll(pageable).map(userMapper::toDTO);
  }

  public Optional<UserDTO> getUserbyId(long id) {
    return userRepository.findById(id).map(userMapper::toDTO);
  }

  public Optional<UserDTO> getUserByUsername(String username) {
    return userRepository.findByusername(username).map(userMapper::toDTO);
  }

  public List<UserDTO> getUsersByIds(List<Long> ids) {
    return userRepository.findAllById(ids).stream().map(userMapper::toDTO).toList();
  }

  public Optional<User> findByusername(String username) throws UsernameNotFoundException {
    return userRepository.findByusername(username);
  }

  @Transactional
  public UserDTO createUser(UserDTO newUserDTO) {
    // Check if username already exists
    if (userRepository.findByusername(newUserDTO.getUsername()).isPresent()) {
      throw new UserAlreadyExistsException("Username already exists: " + newUserDTO.getUsername());
    }

    // Check if email already exists
    Optional<User> existingEmail = userRepository.findByEmail(newUserDTO.getEmail());
    if (existingEmail.isPresent()) {
      throw new UserAlreadyExistsException("Email already exists: " + newUserDTO.getEmail());
    }

    // Encode password and create user
    User user = userMapper.toEntityWithPasswordEncoding(newUserDTO, passwordEncoder);

    // Assign default role
    Role userRole =
        roleRepository
            .findByName("ROLE_USER")
            .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
    user.setRoles(Collections.singleton(userRole));

    User savedUser = userRepository.save(user);

    publishUserEvent("USER_CREATED", savedUser);

    return userMapper.toDTO(savedUser);
  }

  @Transactional
  public UserDTO updateUser(Long id, UpdateUserDTO updateDTO) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

    // Update only provided fields (null-safe partial update)
    if (updateDTO.getFirstName() != null && !updateDTO.getFirstName().isBlank()) {
      user.setFirstName(updateDTO.getFirstName());
    }
    if (updateDTO.getLastName() != null && !updateDTO.getLastName().isBlank()) {
      user.setLastName(updateDTO.getLastName());
    }
    if (updateDTO.getEmail() != null && !updateDTO.getEmail().isBlank()) {
      // Check if email is already taken by another user
      Optional<User> existingEmail = userRepository.findByEmail(updateDTO.getEmail());
      if (existingEmail.isPresent() && !existingEmail.get().getId().equals(id)) {
        throw new UserAlreadyExistsException("Email already exists: " + updateDTO.getEmail());
      }
      user.setEmail(updateDTO.getEmail());
    }
    if (updateDTO.getPassword() != null && !updateDTO.getPassword().isBlank()) {
      user.setPassword(passwordEncoder.encode(updateDTO.getPassword()));
    }

    User updatedUser = userRepository.save(user);

    publishUserEvent("USER_UPDATED", updatedUser);

    return userMapper.toDTO(updatedUser);
  }

  @Transactional
  public void deleteUser(Long id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    userRepository.deleteById(id);

    publishUserEvent("USER_DELETED", user);
  }

  private void publishUserEvent(String eventType, User user) {
    if (eventPublisher == null) {
      return;
    }
    String fullName =
        (user.getFirstName() != null ? user.getFirstName() : "")
            + " "
            + (user.getLastName() != null ? user.getLastName() : "");
    UserEvent event =
        UserEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(eventType)
            .userId(user.getId())
            .username(
                user.getActualUsername() != null
                    ? user.getActualUsername()
                    : (user.getUsername() != null ? user.getUsername() : ""))
            .fullName(fullName.trim())
            .email(user.getEmail())
            .timestamp(Instant.now())
            .build();

    eventPublisher.publish("USER", String.valueOf(user.getId()), eventType, event);
  }

  public Page<UserDTO> searchUsers(String query, Pageable pageable) {
    return userRepository
        .searchByUsernameOrEmailOrFirstName(query, pageable)
        .map(userMapper::toDTO);
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    // Try to parse as ID first if it's numeric to support standardized JWTs
    if (username != null && username.matches("\\d+")) {
      try {
        Long id = Long.parseLong(username);
        Optional<User> userById = userRepository.findById(id);
        if (userById.isPresent()) {
          return userById.get();
        }
      } catch (NumberFormatException ignored) {
        // Fall back to username search
      }
    }

    Optional<User> optionalUser = userRepository.findByusername(username);
    return optionalUser.orElseThrow(
        () -> new UsernameNotFoundException("User not found: " + username));
  }
}
