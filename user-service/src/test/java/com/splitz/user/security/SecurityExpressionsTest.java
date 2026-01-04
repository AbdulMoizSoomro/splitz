package com.splitz.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.splitz.user.dto.UserDTO;
import com.splitz.user.model.User;
import com.splitz.user.service.UserService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class SecurityExpressionsTest {

  @Autowired private SecurityExpressions securityExpressions;

  @Autowired private UserService userService;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private User createTestUser(String suffix) {
    UserDTO dto = new UserDTO();
    dto.setUsername("user_" + suffix);
    dto.setEmail("user_" + suffix + "@example.com");
    dto.setFirstName("First");
    dto.setLastName("Last");
    dto.setPassword("P@ssw0rd");
    userService.createUser(dto);
    return userService.findByusername(dto.getUsername()).orElseThrow();
  }

  @Test
  void isOwnerWhenPrincipalIsEntity() {
    User saved = createTestUser(UUID.randomUUID().toString());
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(saved, null, saved.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);

    boolean ok = securityExpressions.isOwnerOrAdmin(saved.getId());
    assertThat(ok).isTrue();
  }

  @Test
  void isOwnerWhenPrincipalIsSpringSecurityUser_fallbackLoadsEntity() {
    User saved = createTestUser(UUID.randomUUID().toString());
    org.springframework.security.core.userdetails.User principal =
        new org.springframework.security.core.userdetails.User(
            saved.getUsername(), "irrelevant", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);

    boolean ok = securityExpressions.isOwnerOrAdmin(saved.getId());
    assertThat(ok).isTrue();
  }

  @Test
  void returnsFalseForDifferentUser() {
    User a = createTestUser(UUID.randomUUID().toString());
    User b = createTestUser(UUID.randomUUID().toString());

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(a, null, a.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);

    boolean ok = securityExpressions.isOwnerOrAdmin(b.getId());
    assertThat(ok).isFalse();
  }

  @Test
  void nullTargetReturnsFalse() {
    User saved = createTestUser(UUID.randomUUID().toString());
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(saved, null, saved.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);

    boolean ok = securityExpressions.isOwnerOrAdmin(null);
    assertThat(ok).isFalse();
  }

  @Test
  void adminAuthorityAlwaysAllowed() {
    User saved = createTestUser(UUID.randomUUID().toString());
    // use a principal without id but with ADMIN authority
    org.springframework.security.core.userdetails.User principal =
        new org.springframework.security.core.userdetails.User(
            "someone", "x", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);

    boolean ok = securityExpressions.isOwnerOrAdmin(99999L);
    assertThat(ok).isTrue();
  }
}
