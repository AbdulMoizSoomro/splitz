package com.splitz.security.authorization;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component("splitzAuthorizer")
public class SharedSecurityAuthorizer {

  /**
   * Checks if the authenticated user is the owner of the resource or has an ADMIN role.
   *
   * @param targetUserId The ID of the user who owns the resource.
   * @return true if authorized, false otherwise.
   */
  public boolean isSelfOrAdmin(Long targetUserId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }

    Object principal = authentication.getPrincipal();
    if (!(principal instanceof UserDetails userDetails)) {
      return false;
    }

    // In our stateless system, the username is the userId (String)
    try {
      Long currentUserId = Long.parseLong(userDetails.getUsername());
      boolean isOwner = currentUserId.equals(targetUserId);
      boolean isAdmin =
          userDetails.getAuthorities().stream()
              .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

      return isOwner || isAdmin;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Checks if the authenticated user has an ADMIN role.
   *
   * @return true if authorized, false otherwise.
   */
  public boolean isAdmin() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }

    Object principal = authentication.getPrincipal();
    if (!(principal instanceof UserDetails userDetails)) {
      return false;
    }

    return userDetails.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }
}
