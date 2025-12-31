package com.splitz.user.integration;

import com.splitz.user.model.Role;
import com.splitz.user.model.User;
import com.splitz.user.repository.RoleRepository;
import com.splitz.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashSet;
import java.util.Set;

@Configuration
@Profile("test")
public class TestDataInitializer {

    @Bean
    public CommandLineRunner initTestData(UserRepository userRepository,
            RoleRepository roleRepository,
            BCryptPasswordEncoder passwordEncoder) {
        return args -> {
            // Create roles if they don't exist
            Role userRole;
            if (roleRepository.findByName("ROLE_USER").isEmpty()) {
                userRole = roleRepository.save(new Role("ROLE_USER"));
            } else {
                userRole = roleRepository.findByName("ROLE_USER").get();
            }

            Role adminRole;
            if (roleRepository.findByName("ROLE_ADMIN").isEmpty()) {
                adminRole = roleRepository.save(new Role("ROLE_ADMIN"));
            } else {
                adminRole = roleRepository.findByName("ROLE_ADMIN").get();
            }

            // Create test user if doesn't exist
            if (userRepository.findByusername("testuser").isEmpty()) {
                Set<Role> userRoles = new HashSet<>();
                userRoles.add(userRole);

                User testUser = new User(
                        "Test",
                        "testuser",
                        passwordEncoder.encode("password123"),
                        userRoles);
                testUser.setEmail("testuser@example.com");
                testUser.setLastName("User");
                testUser.setEnabled(true);
                testUser.setVerified(true);
                userRepository.save(testUser);
            }

            // Create admin user if doesn't exist
            if (userRepository.findByusername("adminuser").isEmpty()) {
                Set<Role> adminRoles = new HashSet<>();
                adminRoles.add(adminRole);

                User adminUser = new User(
                        "Admin",
                        "adminuser",
                        passwordEncoder.encode("admin123"),
                        adminRoles);
                adminUser.setEmail("admin@example.com");
                adminUser.setLastName("Administrator");
                adminUser.setEnabled(true);
                adminUser.setVerified(true);
                userRepository.save(adminUser);
            }
        };
    }
}
