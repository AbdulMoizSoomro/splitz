package com.splitz.expense.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class WebClientConfig {

  @Value("${services.user-service.url}")
  private String userServiceUrl;

  @Bean
  public WebClient userWebClient(WebClient.Builder builder) {
    return builder.baseUrl(userServiceUrl).filter(addBearerToken()).build();
  }

  private ExchangeFilterFunction addBearerToken() {
    return ExchangeFilterFunction.ofRequestProcessor(
        clientRequest -> {
          Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
          if (authentication != null && authentication.getCredentials() instanceof String token) {
            return Mono.just(
                ClientRequest.from(clientRequest)
                    .header("Authorization", "Bearer " + token)
                    .build());
          }
          return Mono.just(clientRequest);
        });
  }
}
