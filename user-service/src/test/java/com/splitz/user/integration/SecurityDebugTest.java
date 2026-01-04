package com.splitz.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.splitz.user.model.Role;
import com.splitz.user.model.User;
import com.splitz.user.repository.RoleRepository;
import com.splitz.user.repository.UserRepository;
import com.splitz.user.security.SecurityExpressions;
import com.splitz.user.service.UserService;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SecurityDebugTest {

  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private BCryptPasswordEncoder passwordEncoder;
  @Autowired private SecurityExpressions securityExpressions;
  @Autowired private UserService userService;

  private Long adminUserId;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();

    Role adminRole =
        roleRepository
            .findByName("ROLE_ADMIN")
            .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));

    Set<Role> adminRoles = new HashSet<>();
    adminRoles.add(adminRole);

    User adminUser = new User("Admin", "adminuser", passwordEncoder.encode("admin123"), adminRoles);
    adminUser.setEmail("admin@example.com");
    adminUser.setLastName("Administrator");
    adminUser.setEnabled(true);
    adminUser.setVerified(true);
    adminUserId = userRepository.save(adminUser).getId();
  }

  @Test
  void testAdminUserHasCorrectRoles() {
    User admin = userRepository.findByusername("adminuser").orElseThrow();
    System.out.println("Admin user ID: " + admin.getId());
    System.out.println("Admin user roles: " + admin.getRoles());
    System.out.println("Admin user authorities: " + admin.getAuthorities());
    assertThat(admin.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
        .isTrue();
  }

  @Test
  void testSecurityExpressionsWithAdminUser() {
    User admin = userRepository.findByusername("adminuser").orElseThrow();

    // Set up security context with admin user
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);

    System.out.println("Auth principal: " + auth.getPrincipal());
    System.out.println("Auth authorities: " + auth.getAuthorities());

    boolean result = securityExpressions.isOwnerOrAdmin(99999L);
    System.out.println("isOwnerOrAdmin(99999L) = " + result);

    assertThat(result).isTrue();

    SecurityContextHolder.clearContext();
  }
}
