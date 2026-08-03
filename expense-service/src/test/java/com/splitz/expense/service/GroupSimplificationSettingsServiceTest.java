package com.splitz.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.model.GroupSimplificationSettings;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.repository.GroupSimplificationSettingsRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link GroupSimplificationSettingsService} is the single home for reading a group's
 * simplification settings — including the "enabled, INTRA_GROUP, no opt-outs" default — so the plan
 * flow reads the truth from here instead of rebuilding the default itself.
 */
class GroupSimplificationSettingsServiceTest {

  private GroupSimplificationSettingsRepository settingsRepository;
  private GroupSimplificationSettingsService settingsService;

  private static final Long GROUP_ID = 10L;

  @BeforeEach
  void setUp() {
    settingsRepository = mock(GroupSimplificationSettingsRepository.class);
    settingsService = new GroupSimplificationSettingsService(settingsRepository);
  }

  @Test
  @DisplayName("Should return the default settings when none are persisted")
  void readSettings_defaultsWhenNoneExist() {
    when(settingsRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

    GroupSimplificationSettings settings = settingsService.readSettings(GROUP_ID);

    assertThat(settings.getGroupId()).isEqualTo(GROUP_ID);
    assertThat(settings.isSimplificationEnabled()).isTrue();
    assertThat(settings.getSimplificationScope()).isEqualTo(SimplificationScope.INTRA_GROUP);
    assertThat(settings.getOptOutUserIds()).isEmpty();
    verify(settingsRepository).findByGroupId(GROUP_ID);
  }

  @Test
  @DisplayName("Should return the persisted settings when they exist")
  void readSettings_returnsPersistedWhenExist() {
    GroupSimplificationSettings persisted =
        GroupSimplificationSettings.builder()
            .groupId(GROUP_ID)
            .simplificationEnabled(false)
            .simplificationScope(SimplificationScope.CROSS_GROUP)
            .optOutUserIds(Set.of(7L))
            .build();
    when(settingsRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(persisted));

    GroupSimplificationSettings settings = settingsService.readSettings(GROUP_ID);

    assertThat(settings).isSameAs(persisted);
  }
}
