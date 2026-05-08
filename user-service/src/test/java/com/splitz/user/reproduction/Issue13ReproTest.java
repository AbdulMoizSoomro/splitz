package com.splitz.user.reproduction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.user.security.AuthController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Issue13ReproTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void loginAndGetMe_ShouldWork_WhenStandardizedOnId() throws Exception {
    // 1. Register a user
    String unique = UUID.randomUUID().toString().substring(0, 8);
    java.util.Map<String, Object> registration = new java.util.HashMap<>();
    registration.put("username", "user_" + unique);
    registration.put("email", "email_" + unique + "@example.com");
    registration.put("firstName", "First");
    registration.put("lastName", "Last");
    registration.put("password", "Password123!");

    mockMvc
        .perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isCreated());

    // 2. Authenticate
    AuthController.JwtRequest authRequest =
        new AuthController.JwtRequest("user_" + unique, "Password123!");
    MvcResult authResult =
        mockMvc
            .perform(
                post("/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(authRequest)))
            .andExpect(status().isOk())
            .andReturn();

    String responseJson = authResult.getResponse().getContentAsString();
    String token = objectMapper.readTree(responseJson).get("token").asText();

    // 3. Call /users/me
    mockMvc
        .perform(get("/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("user_" + unique));
  }
}
