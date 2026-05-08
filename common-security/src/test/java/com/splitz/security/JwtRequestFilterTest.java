package com.splitz.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

@ExtendWith(MockitoExtension.class)
class JwtRequestFilterTest {

  @Mock private UserDetailsService userDetailsService;

  @Mock private JwtUtil jwtUtil;

  @Mock private HttpServletRequest request;

  @Mock private HttpServletResponse response;

  @Mock private FilterChain filterChain;

  @InjectMocks private JwtRequestFilter jwtRequestFilter;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_ShouldContinueChain_WhenNoAuthHeader() throws ServletException, IOException {
    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

    jwtRequestFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void doFilter_ShouldContinueChain_WhenAuthHeaderDoesNotStartWithBearer()
      throws ServletException, IOException {
    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic auth");

    jwtRequestFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void doFilter_ShouldAuthenticateStatelessly_WhenTokenHasClaims()
      throws ServletException, IOException {
    String token = "stateless.token.here";
    String username = "123";
    Long userId = 123L;
    java.util.List<String> roles = java.util.List.of("ROLE_USER");

    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
    when(jwtUtil.extractUsername(token)).thenReturn(username);
    when(jwtUtil.extractUserId(token)).thenReturn(userId);
    when(jwtUtil.extractRoles(token)).thenReturn(roles);
    // isTokenValid is called with a new User object, so we use any() or match attributes
    when(jwtUtil.isTokenValid(
            org.mockito.ArgumentMatchers.eq(token),
            org.mockito.ArgumentMatchers.any(UserDetails.class)))
        .thenReturn(true);

    jwtRequestFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(jwtUtil).extractUserId(token);
    verify(jwtUtil).extractRoles(token);
    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void doFilter_ShouldAuthenticate_WhenTokenIsValid() throws ServletException, IOException {
    String token = "valid.token.here";
    String username = "testuser";
    UserDetails userDetails = new User(username, "password", new ArrayList<>());

    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
    when(jwtUtil.extractUsername(token)).thenReturn(username);
    // Return null for userId to trigger fallback
    when(jwtUtil.extractUserId(token)).thenReturn(null);
    when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
    when(jwtUtil.isTokenValid(token, userDetails)).thenReturn(true);

    jwtRequestFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(userDetailsService).loadUserByUsername(username);
    verify(jwtUtil).isTokenValid(token, userDetails);
    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void doFilter_ShouldNotAuthenticate_WhenTokenIsInvalid() throws ServletException, IOException {
    String token = "invalid.token.here";
    String username = "testuser";
    UserDetails userDetails = new User(username, "password", new ArrayList<>());

    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
    when(jwtUtil.extractUsername(token)).thenReturn(username);
    // Return null for userId to trigger fallback
    when(jwtUtil.extractUserId(token)).thenReturn(null);
    when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
    when(jwtUtil.isTokenValid(token, userDetails)).thenReturn(false);

    jwtRequestFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(userDetailsService).loadUserByUsername(username);
    verify(jwtUtil).isTokenValid(token, userDetails);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void doFilter_ShouldContinueChain_WhenExtractUsernameThrowsException()
      throws ServletException, IOException {
    String token = "invalid.token.here";

    when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
    when(jwtUtil.extractUsername(token)).thenThrow(new RuntimeException("Invalid token"));

    jwtRequestFilter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }
}
