package com.ako.dbuff.service.replay.analysis.item;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParameterNamesTest {

  @Test
  void ofUnitesTheParts() {
    assertThat(ParameterNames.of(Set.of("a", "b"), Set.of("b", "c")))
        .containsExactlyInAnyOrder("a", "b", "c");
  }

  @Test
  void withPricesAddsThePriceNames() {
    assertThat(ParameterNames.withPrices(Set.of("a")))
        .contains("a")
        .containsAll(WorthPrices.NAMES)
        .hasSize(WorthPrices.NAMES.size() + 1);
  }

  @Test
  void theResultIsImmutable() {
    Set<String> names = ParameterNames.of(Set.of("a"));
    assertThatThrownBy(() -> names.add("x")).isInstanceOf(UnsupportedOperationException.class);
  }
}
