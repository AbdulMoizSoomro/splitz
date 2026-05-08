package com.splitz.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.user.dto.UserDTO;
import com.splitz.user.mapper.UserMapper;
import com.splitz.user.model.User;
import com.splitz.user.repository.RoleRepository;
import com.splitz.user.repository.UserRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private UserMapper userMapper;
  @Mock private BCryptPasswordEncoder passwordEncoder;

  @InjectMocks private UserService userService;

  @Test
  @DisplayName("Should return paginated users")
  void testGetAllUsers_WhenCalledWithPageable_ThenReturnsPage() {
    // Arrange
    Pageable pageable = PageRequest.of(0, 10);
    User user1 = new User();
    user1.setId(1L);
    User user2 = new User();
    user2.setId(2L);
    List<User> users = Arrays.asList(user1, user2);
    Page<User> userPage = new PageImpl<>(users, pageable, 2);

    UserDTO dto1 = new UserDTO();
    dto1.setId(1L);
    UserDTO dto2 = new UserDTO();
    dto2.setId(2L);

    when(userRepository.findAll(any(Pageable.class))).thenReturn(userPage);
    when(userMapper.toDTO(user1)).thenReturn(dto1);
    when(userMapper.toDTO(user2)).thenReturn(dto2);

    // Act
    Page<UserDTO> result = userService.getAllUsers(pageable);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent().get(0).getId()).isEqualTo(1L);

    verify(userRepository, times(1)).findAll(pageable);
  }
}
