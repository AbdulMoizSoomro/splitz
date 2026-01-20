package com.splitz.expense.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.dto.UserResponse;
import java.io.IOException;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class WebClientUserClientTest {

  private static MockWebServer mockBackEnd;
  private WebClientUserClient userClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeAll
  static void setUp() throws IOException {
    mockBackEnd = new MockWebServer();
    mockBackEnd.start();
  }

  @AfterAll
  static void tearDown() throws IOException {
    mockBackEnd.shutdown();
  }

  @BeforeEach
  void initialize() {
    String baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
    userClient = new WebClientUserClient(webClient);
  }

  @Test
  void getUserById_WhenUserExists_ReturnsUser() throws Exception {
    UserResponse mockResponse =
        UserResponse.builder().id(1L).username("testuser").email("test@example.com").build();

    mockBackEnd.enqueue(
        new MockResponse()
            .setBody(objectMapper.writeValueAsString(mockResponse))
            .addHeader("Content-Type", "application/json"));

    Optional<UserResponse> result = userClient.getUserById(1L);

    assertThat(result).isPresent();
    assertThat(result.get().getUsername()).isEqualTo("testuser");
  }

  @Test
  void getUserById_WhenUserDoesNotExist_ReturnsEmpty() {
    mockBackEnd.enqueue(new MockResponse().setResponseCode(404));

    Optional<UserResponse> result = userClient.getUserById(1L);

    assertThat(result).isEmpty();
  }

  @Test
  void existsById_WhenUserExists_ReturnsTrue() throws Exception {
    UserResponse mockResponse = UserResponse.builder().id(1L).build();

    mockBackEnd.enqueue(
        new MockResponse()
            .setBody(objectMapper.writeValueAsString(mockResponse))
            .addHeader("Content-Type", "application/json"));

    boolean result = userClient.existsById(1L);

    assertThat(result).isTrue();
  }

  @Test
  void existsById_WhenUserDoesNotExist_ReturnsFalse() {
    mockBackEnd.enqueue(new MockResponse().setResponseCode(404));

    boolean result = userClient.existsById(1L);

    assertThat(result).isFalse();
  }
}
