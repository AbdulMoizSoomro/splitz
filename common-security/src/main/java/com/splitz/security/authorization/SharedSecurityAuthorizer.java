package com.splitz.security.authorization;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Slf4j
@Component("splitzAuthorizer")
public class SharedSecurityAuthorizer {

  private Optional<Long> resolveCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }

    Object principal = authentication.getPrincipal();
    if (!(principal instanceof UserDetails userDetails)) {
      return Optional.empty();
    }

    try {
      return Optional.of(Long.parseLong(userDetails.getUsername()));
    } catch (NumberFormatException e) {
      log.info("Invalid user principal username (not a numeric ID): {}", userDetails.getUsername());
      return Optional.empty();
    }
  }

  public Long getCurrentUserId() {
    return resolveCurrentUserId()
        .orElseThrow(() -> new AccessDeniedException("No authenticated user found"));
  }

  public Set<String> getCurrentRoles() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AccessDeniedException("No authenticated user found");
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }

  public boolean isSelfOrAdmin(Long targetUserId) {
    return resolveCurrentUserId()
        .map(currentUserId -> currentUserId.equals(targetUserId) || isAdmin())
        .orElse(false);
  }

  public boolean isAdmin() {
    try {
      return getCurrentRoles().contains("ROLE_ADMIN");
    } catch (AccessDeniedException e) {
      return false;
    }
  }
}
