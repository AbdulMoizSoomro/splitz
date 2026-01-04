package com.splitz.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing configuration to automatically populate @CreatedDate and @LastModifiedDate fields
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
