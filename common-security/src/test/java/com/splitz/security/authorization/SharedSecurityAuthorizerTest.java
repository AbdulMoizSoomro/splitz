package com.splitz.security.authorization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class SharedSecurityAuthorizerTest {

  private SharedSecurityAuthorizer authorizer;

  @BeforeEach
  void setUp() {
    authorizer = new SharedSecurityAuthorizer();
    SecurityContextHolder.clearContext();
  }

  @Test
  void isSelfOrAdmin_ShouldReturnTrue_WhenUserIsSelf() {
    setupAuthentication("123", List.of("ROLE_USER"));
    assertTrue(authorizer.isSelfOrAdmin(123L));
  }

  @Test
  void isSelfOrAdmin_ShouldReturnTrue_WhenUserIsAdmin() {
    setupAuthentication("456", List.of("ROLE_ADMIN"));
    assertTrue(authorizer.isSelfOrAdmin(123L));
  }

  @Test
  void isSelfOrAdmin_ShouldReturnFalse_WhenUserIsNeitherSelfNorAdmin() {
    setupAuthentication("456", List.of("ROLE_USER"));
    assertFalse(authorizer.isSelfOrAdmin(123L));
  }

  @Test
  void isAdmin_ShouldReturnTrue_WhenUserIsAdmin() {
    setupAuthentication("123", List.of("ROLE_ADMIN"));
    assertTrue(authorizer.isAdmin());
  }

  @Test
  void isAdmin_ShouldReturnFalse_WhenUserIsNotAdmin() {
    setupAuthentication("123", List.of("ROLE_USER"));
    assertFalse(authorizer.isAdmin());
  }

  @Test
  void getCurrentUserId_ShouldReturnUserId_WhenAuthenticated() {
    setupAuthentication("123", List.of("ROLE_USER"));
    assertEquals(123L, authorizer.getCurrentUserId());
  }

  @Test
  void getCurrentUserId_ShouldThrowAccessDeniedException_WhenNotAuthenticated() {
    SecurityContextHolder.clearContext();
    assertThrows(AccessDeniedException.class, () -> authorizer.getCurrentUserId());
  }

  @Test
  void getCurrentUserId_ShouldThrowAccessDeniedException_WhenInvalidUserIdFormat() {
    setupAuthentication("abc", List.of("ROLE_USER"));
    assertThrows(AccessDeniedException.class, () -> authorizer.getCurrentUserId());
  }

  private void setupAuthentication(String userId, List<String> roles) {
    List<SimpleGrantedAuthority> authorities =
        roles.stream().map(SimpleGrantedAuthority::new).toList();
    UserDetails userDetails = new User(userId, "", authorities);
    Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
    SecurityContext securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(auth);
    SecurityContextHolder.setContext(securityContext);
  }
}
