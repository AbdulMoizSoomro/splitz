package com.splitz.user.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import com.splitz.user.model.Role;
import com.splitz.user.repository.RoleRepository;

/**
 * DataInitializer ensures that default roles exist in the database on
 * application startup.
 * This is a safety mechanism in addition to Flyway migrations.
 * 
 * Why both Flyway and DataInitializer?
 * - Flyway V4 migration seeds roles for PostgreSQL production databases
 * - DataInitializer handles H2 in-memory databases and provides runtime safety
 */
@Configuration
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    @Transactional
    public CommandLineRunner initializeRoles(RoleRepository roleRepository) {
        return args -> {
            logger.info("Checking for default roles...");

            // Check and create ROLE_USER
            if (roleRepository.findByName("ROLE_USER").isEmpty()) {
                Role userRole = new Role("ROLE_USER");
                roleRepository.save(userRole);
                logger.info("✅ Created default role: ROLE_USER");
            } else {
                logger.info("✓ ROLE_USER already exists");
            }

            // Check and create ROLE_ADMIN
            if (roleRepository.findByName("ROLE_ADMIN").isEmpty()) {
                Role adminRole = new Role("ROLE_ADMIN");
                roleRepository.save(adminRole);
                logger.info("✅ Created default role: ROLE_ADMIN");
            } else {
                logger.info("✓ ROLE_ADMIN already exists");
            }

            logger.info("Role initialization complete. Total roles: {}", roleRepository.count());
        };
    }
}
