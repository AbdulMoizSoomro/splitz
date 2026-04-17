package com.splitz.expense.client;

import com.splitz.expense.dto.UserResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class WebClientUserClient implements UserClient {

  private final WebClient userWebClient;

  public WebClientUserClient(WebClient userWebClient) {
    this.userWebClient = userWebClient;
  }

  @Override
  public Optional<UserResponse> getUserById(Long id) {
    log.info("Fetching user with id: {}", id);
    return userWebClient
        .get()
        .uri("/users/{id}", id)
        .retrieve()
        .onStatus(status -> status.equals(HttpStatus.NOT_FOUND), response -> Mono.empty())
        .bodyToMono(UserResponse.class)
        .blockOptional();
  }

  @Override
  public List<UserResponse> getUsersByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    log.info("Fetching users with ids: {}", ids);
    String idsParam = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    return userWebClient
        .get()
        .uri(uriBuilder -> uriBuilder.path("/users/bulk").queryParam("ids", idsParam).build())
        .retrieve()
        .bodyToFlux(UserResponse.class)
        .collectList()
        .block();
  }

  @Override
  public boolean existsById(Long id) {
    log.info("Checking existence of user with id: {}", id);
    return getUserById(id).isPresent();
  }
}
