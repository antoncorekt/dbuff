package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompositionTokenTest {

  @Test
  void everyTokenAndEveryFunctionHasWording() {
    for (CompositionToken token : CompositionToken.values()) {
      assertThat(CompositionToken.words(token.name())).isNotBlank().doesNotContain("_");
    }
    for (RoleFunction function : RoleFunction.values()) {
      assertThat(function.words()).isNotBlank().doesNotContain("_");
    }
  }

  @Test
  void countsAreWordedFromTheirNumber() {
    assertThat(CompositionToken.words("CARRIES_0")).isEqualTo("no carry");
    assertThat(CompositionToken.words("CARRIES_1")).isEqualTo("1 carry");
    assertThat(CompositionToken.words("SUPPORTS_5")).isEqualTo("5 supports");
  }

  @Test
  void aTokenNobodyKnowsIsWrittenInLowerCase() {
    assertThat(CompositionToken.words("NO_SMOKE_GANK")).isEqualTo("no smoke gank");
  }
}
