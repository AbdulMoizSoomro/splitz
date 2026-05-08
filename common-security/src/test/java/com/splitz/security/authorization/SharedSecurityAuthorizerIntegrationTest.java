package com.splitz.security.authorization;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.parameters.P;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
class SharedSecurityAuthorizerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void whenUserIsSelf_thenAllow() throws Exception {
    mockMvc
        .perform(get("/test/self/123").with(user("123").roles("USER")))
        .andExpect(status().isOk());
  }

  @Test
  void whenUserIsNotSelfAndNotAdmin_thenForbidden() throws Exception {
    mockMvc
        .perform(get("/test/self/123").with(user("456").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void whenUserIsAdmin_thenAllow() throws Exception {
    mockMvc
        .perform(get("/test/self/123").with(user("456").roles("ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void whenAdminOnlyAndUserIsAdmin_thenAllow() throws Exception {
    mockMvc.perform(get("/test/admin").with(user("456").roles("ADMIN"))).andExpect(status().isOk());
  }

  @Test
  void whenAdminOnlyAndUserIsNotAdmin_thenForbidden() throws Exception {
    mockMvc
        .perform(get("/test/admin").with(user("123").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @SpringBootApplication
  @EnableMethodSecurity
  @Import({SharedSecurityAuthorizer.class, TestController.class})
  static class TestConfig {}

  @RestController
  static class TestController {
    @GetMapping("/test/self/{userId}")
    @PreAuthorize("@splitzAuthorizer.isSelfOrAdmin(#userId)")
    public String selfOrAdmin(@P("userId") @PathVariable("userId") Long userId) {
      return "ok";
    }

    @GetMapping("/test/admin")
    @PreAuthorize("@splitzAuthorizer.isAdmin()")
    public String adminOnly() {
      return "ok";
    }
  }
}
