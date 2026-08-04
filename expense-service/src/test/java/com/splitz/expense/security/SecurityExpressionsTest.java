package com.splitz.expense.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.governance.GroupGovernance;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

/**
 * Unit tests for the SecurityExpressions SpEL adapter. The seam under test is the
 * SecurityExpressions interface — the bridge between @PreAuthorize SpEL and GroupGovernance.
 */
class SecurityExpressionsTest {

  @Mock private GroupGovernance groupGovernance;
  @Mock private SharedSecurityAuthorizer splitzAuthorizer;

  private SecurityExpressions securityExpressions;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    securityExpressions = new SecurityExpressions(groupGovernance, splitzAuthorizer);
  }

  // ---- isGroupMember ----

  @Test
  void isGroupMember_returnsTrue_whenUserIsMember() {
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(1L);
    when(groupGovernance.isMember(10L, 1L)).thenReturn(true);

    assertThat(securityExpressions.isGroupMember(10L)).isTrue();
  }

  @Test
  void isGroupMember_returnsTrue_whenGlobalAdminNotMember() {
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(1L);
    when(splitzAuthorizer.isAdmin()).thenReturn(true);
    when(groupGovernance.isMember(10L, 1L)).thenReturn(false);

    assertThat(securityExpressions.isGroupMember(10L)).isTrue();
  }

  @Test
  void isGroupMember_returnsFalse_whenNotAuthenticated() {
    when(splitzAuthorizer.getCurrentUserId())
        .thenThrow(new AccessDeniedException("not authenticated"));

    assertThat(securityExpressions.isGroupMember(10L)).isFalse();
  }

  // ---- isGroupAdmin ----

  @Test
  void isGroupAdmin_returnsTrue_whenAuthorized() {
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(1L);
    // assertCanManageGroup returns void; no stubbing needed for success

    assertThat(securityExpressions.isGroupAdmin(10L)).isTrue();
  }

  @Test
  void isGroupAdmin_returnsFalse_whenUnauthorized() {
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(1L);
    doThrow(new UnauthorizedException("not admin"))
        .when(groupGovernance)
        .assertCanManageGroup(eq(10L), eq(1L));

    assertThat(securityExpressions.isGroupAdmin(10L)).isFalse();
  }

  @Test
  void isGroupAdmin_propagatesGroupNotFound() {
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(1L);
    doThrow(new ResourceNotFoundException("Group not found"))
        .when(groupGovernance)
        .assertCanManageGroup(eq(10L), eq(1L));

    assertThatThrownBy(() -> securityExpressions.isGroupAdmin(10L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Group not found");
  }

  @Test
  void isGroupAdmin_returnsFalse_whenNotAuthenticated() {
    when(splitzAuthorizer.getCurrentUserId())
        .thenThrow(new AccessDeniedException("not authenticated"));

    assertThat(securityExpressions.isGroupAdmin(10L)).isFalse();
  }

  @Test
  void isGroupAdmin_propagatesInfrastructureExceptions() {
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(1L);
    doThrow(new RuntimeException("DB connection lost"))
        .when(groupGovernance)
        .assertCanManageGroup(eq(10L), eq(1L));

    assertThatThrownBy(() -> securityExpressions.isGroupAdmin(10L))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("DB connection lost");
  }
}
