package com.splitz.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.dto.UserSimplificationPreferenceDTO;
import com.splitz.expense.model.UserSimplificationPreference;
import com.splitz.expense.repository.UserSimplificationPreferenceRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link UserSimplificationPreferenceService} is the single home for the account-level
 * opt-out invariant: an account opt-out is a hard override that excludes the user from netting in
 * every group, so the effective opt-out set is the union of a group's configured opt-outs and the
 * account-level opt-outs of its members.
 */
class UserSimplificationPreferenceServiceTest {

  private UserSimplificationPreferenceRepository preferenceRepository;
  private UserSimplificationPreferenceService preferenceService;

  private static final Long USER_ID = 5L;

  @BeforeEach
  void setUp() {
    preferenceRepository = mock(UserSimplificationPreferenceRepository.class);
    preferenceService = new UserSimplificationPreferenceService(preferenceRepository);
  }

  @Test
  @DisplayName("Should default to not opted out when no preference row exists")
  void readPreference_defaultsToOptedIn() {
    when(preferenceRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

    UserSimplificationPreference preference = preferenceService.readPreference(USER_ID);

    assertThat(preference.getUserId()).isEqualTo(USER_ID);
    assertThat(preference.isAccountOptOut()).isFalse();
  }

  @Test
  @DisplayName("Should persist an opt-out as the hard account-level override")
  void setAccountOptOut_persistsOverride() {
    when(preferenceRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(preferenceRepository.save(any(UserSimplificationPreference.class)))
        .thenAnswer(
            invocation -> {
              UserSimplificationPreference saved = invocation.getArgument(0);
              saved.setId(1L);
              return saved;
            });

    UserSimplificationPreferenceDTO saved = preferenceService.setAccountOptOut(USER_ID, true);

    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.isAccountOptOut()).isTrue();
    verify(preferenceRepository).save(any(UserSimplificationPreference.class));
  }

  @Test
  @DisplayName("Should clear an existing opt-out when opting back in")
  void setAccountOptOut_clearsOverride() {
    UserSimplificationPreference existing =
        UserSimplificationPreference.builder().userId(USER_ID).accountOptOut(true).build();
    existing.setId(3L);
    when(preferenceRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
    when(preferenceRepository.save(any(UserSimplificationPreference.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UserSimplificationPreferenceDTO saved = preferenceService.setAccountOptOut(USER_ID, false);

    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.isAccountOptOut()).isFalse();
  }

  @Test
  @DisplayName("Should union group opt-outs with account-level overrides of the group's members")
  void effectiveOptOutUserIds_unionsAccountOverrides() {
    // Group config opts out 7L; account preference independently opts out 9L, who is a member.
    when(preferenceRepository.findOptedOutUserIdsIn(Set.of(7L, 9L, 11L))).thenReturn(Set.of(9L));

    Set<Long> effective =
        preferenceService.effectiveOptOutUserIds(Set.of(7L), List.of(7L, 9L, 11L));

    assertThat(effective).containsExactlyInAnyOrder(7L, 9L);
  }

  @Test
  @DisplayName("Should return group opt-outs untouched when no member has an account override")
  void effectiveOptOutUserIds_returnsGroupOptOutsWhenNoOverrides() {
    when(preferenceRepository.findOptedOutUserIdsIn(Set.of(7L))).thenReturn(Set.of());

    Set<Long> effective = preferenceService.effectiveOptOutUserIds(Set.of(7L), List.of(7L));

    assertThat(effective).containsExactly(7L);
  }

  @Test
  @DisplayName("Should not query the repository for a group with no members")
  void effectiveOptOutUserIds_skipsQueryWhenNoMembers() {
    Set<Long> effective = preferenceService.effectiveOptOutUserIds(Set.of(7L), List.of());

    assertThat(effective).containsExactly(7L);
    verify(preferenceRepository, never()).findOptedOutUserIdsIn(any());
  }

  @Test
  @DisplayName("Should not match account overrides against users outside the group")
  void effectiveOptOutUserIds_scopesOverrideLookupToMembers() {
    when(preferenceRepository.findOptedOutUserIdsIn(Set.of(9L))).thenReturn(Set.of(9L));

    preferenceService.effectiveOptOutUserIds(Set.of(), List.of(9L));

    verify(preferenceRepository).findOptedOutUserIdsIn(Set.of(9L));
    verify(preferenceRepository, never()).findByUserId(anyLong());
  }
}
