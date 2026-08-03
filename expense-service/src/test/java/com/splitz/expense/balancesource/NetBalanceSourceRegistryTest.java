package com.splitz.expense.balancesource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.splitz.expense.model.SimplificationScope;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link NetBalanceSourceRegistry} — the explicit selector that maps a group's {@link
 * SimplificationScope} to its balance-source adapter, so a navigator can see which source a scope
 * uses instead of the decision being hidden behind an enum branch in the orchestrator.
 */
class NetBalanceSourceRegistryTest {

  private NetBalanceSource intraGroup;
  private NetBalanceSource crossGroup;

  @BeforeEach
  void setUp() {
    intraGroup = mock(NetBalanceSource.class);
    crossGroup = mock(NetBalanceSource.class);
  }

  private NetBalanceSourceRegistry registryOf() {
    return new NetBalanceSourceRegistry(List.of(intraGroup, crossGroup));
  }

  @Test
  @DisplayName("Should route INTRA_GROUP to the intra-group source")
  void shouldRouteIntraGroupScope() {
    when(intraGroup.getSupportedScope()).thenReturn(SimplificationScope.INTRA_GROUP);
    when(crossGroup.getSupportedScope()).thenReturn(SimplificationScope.CROSS_GROUP);

    assertThat(registryOf().forScope(SimplificationScope.INTRA_GROUP)).isSameAs(intraGroup);
  }

  @Test
  @DisplayName("Should route CROSS_GROUP to the cross-group source")
  void shouldRouteCrossGroupScope() {
    when(intraGroup.getSupportedScope()).thenReturn(SimplificationScope.INTRA_GROUP);
    when(crossGroup.getSupportedScope()).thenReturn(SimplificationScope.CROSS_GROUP);

    assertThat(registryOf().forScope(SimplificationScope.CROSS_GROUP)).isSameAs(crossGroup);
  }

  @Test
  @DisplayName("Should throw when no source backs a scope")
  void shouldThrowForUnregisteredScope() {
    when(intraGroup.getSupportedScope()).thenReturn(SimplificationScope.INTRA_GROUP);

    NetBalanceSourceRegistry single = new NetBalanceSourceRegistry(List.of(intraGroup));

    assertThatThrownBy(() -> single.forScope(SimplificationScope.CROSS_GROUP))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
