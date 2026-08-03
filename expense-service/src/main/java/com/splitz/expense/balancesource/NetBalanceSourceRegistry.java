package com.splitz.expense.balancesource;

import com.splitz.expense.model.SimplificationScope;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Explicit selector for the {@link NetBalanceSource} adapter seam: maps a group's {@link
 * SimplificationScope} to the balance-source adapter that backs it. The scope genuinely varies
 * across this seam (INTRA_GROUP and CROSS_GROUP are both live), so the selection is made visible
 * here rather than hidden behind an enum branch inside the plan orchestrator. A navigator can read
 * which source each scope uses.
 */
@Component
public class NetBalanceSourceRegistry {

  private final Map<SimplificationScope, NetBalanceSource> sources;

  public NetBalanceSourceRegistry(List<NetBalanceSource> sourceList) {
    this.sources =
        sourceList.stream()
            .collect(Collectors.toMap(NetBalanceSource::getSupportedScope, Function.identity()));
  }

  /**
   * Returns the source backing the given scope.
   *
   * @throws IllegalArgumentException if no source backs the scope
   */
  public NetBalanceSource forScope(SimplificationScope scope) {
    NetBalanceSource source = sources.get(scope);
    if (source == null) {
      throw new IllegalArgumentException("Unsupported simplification scope: " + scope);
    }
    return source;
  }
}
