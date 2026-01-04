package com.splitz.user.security;

import com.splitz.user.model.User;
import com.splitz.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Custom security expressions for use in @PreAuthorize annotations. Provides reusable authorization
 * logic across controllers.
 */
@Component("security")
public class SecurityExpressions {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(SecurityExpressions.class);
  private final UserService userService;

  public SecurityExpressions(UserService userService) {
    this.userService = userService;
  }

  /**
   * Checks if the current user is the owner of the resource or has ADMIN role.
   *
   * @param targetUserId The ID of the user being accessed/modified (accepts Number to handle
   *     Integer/Long SpEL coercion)
   * @return true if user is owner or admin, false otherwise
   */
  public boolean isOwnerOrAdmin(Number targetUserId) {
    if (targetUserId == null) {
      return false;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return false;
    }

    // Check if user has ADMIN role (flexible checks)
    boolean isAdmin =
        auth.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .anyMatch(name -> "ROLE_ADMIN".equals(name) || "ADMIN".equals(name));

    // Debug logging for details during troubleshooting
    if (log.isDebugEnabled()) {
      log.debug(
          "isOwnerOrAdmin invoked: principal={}, authorities={}, targetUserId={}",
          auth.getPrincipal(),
          auth.getAuthorities(),
          targetUserId);
    }

    if (isAdmin) {
      if (log.isDebugEnabled()) {
        log.debug("Access granted: user is admin");
      }
      return true;
    }

    // Determine current user's id preferably from the authentication principal to
    // avoid extra DB calls
    String username = auth.getName();
    Long currentId = null;
    Object principal = auth.getPrincipal();
    if (principal instanceof User) {
      currentId = ((User) principal).getId();
    } else {
      try {
        User currentUser = (User) userService.loadUserByUsername(username);
        currentId = currentUser != null ? currentUser.getId() : null;
      } catch (Exception e) {
        if (log.isDebugEnabled()) {
          log.debug("Failed to load user by username for ownership check: {}", username, e);
        }
        return false;
      }
    }

    if (currentId == null) {
      if (log.isDebugEnabled()) {
        log.debug("Current user id is null — denying ownership for username={}", username);
      }
      return false;
    }

    boolean owner = currentId.longValue() == targetUserId.longValue();
    if (log.isDebugEnabled()) {
      log.debug(
          "Ownership check: currentId={}, target={}, owner={}", currentId, targetUserId, owner);
    }

    return owner;
  }

  /**
   * Checks if the current user has ADMIN role.
   *
   * @return true if user is admin, false otherwise
   */
  public boolean isAdmin() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return false;
    }

    return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }

  /**
   * Checks if the current user is authenticated.
   *
   * @return true if authenticated, false otherwise
   */
  public boolean isAuthenticated() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.isAuthenticated();
  }
}
