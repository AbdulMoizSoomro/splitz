package com.splitz.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.splitz.security.JwtUtil;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class StatelessIdentityIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JwtUtil jwtUtil;

  @MockBean private com.splitz.user.service.UserService userService;

  @Test
  @DisplayName("Should populate SecurityContext principal from JWT claims without DB hit")
  void testStatelessIdentityResolution() {
    // Arrange
    String username = "testuser";
    Long userId = 123L;
    java.util.List<String> roles = Collections.singletonList("ROLE_USER");

    // Generate a token with userId and roles claims
    // We use String.valueOf(userId) as the subject to match user-service convention
    String token = jwtUtil.generateToken(String.valueOf(userId), userId, roles);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    // Act
    // We call a protected endpoint. /users/search?query=test is good.
    ResponseEntity<String> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/users/search?query=test",
            HttpMethod.GET,
            request,
            String.class);

    // Assert
    // The request should be authorized even if UserService is not called
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(userService, never()).loadUserByUsername(anyString());
  }
}
