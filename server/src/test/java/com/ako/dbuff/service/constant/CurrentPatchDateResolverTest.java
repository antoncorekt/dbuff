package com.ako.dbuff.service.constant;

import com.ako.dbuff.service.constant.data.PatchConstant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentPatchDateResolverTest {

  private ConstantsManagers constantsManagers;
  private CurrentPatchDateResolver resolver;

  @BeforeEach
  void setUp() {
    constantsManagers = Mockito.mock(ConstantsManagers.class);
    resolver = new CurrentPatchDateResolver(constantsManagers);
  }

  private void patches(PatchConstant... constants) {
    Map<String, PatchConstant> map = new java.util.LinkedHashMap<>();
    for (PatchConstant constant : constants) {
      map.put(String.valueOf(constant.getId()), constant);
    }
    Mockito.when(constantsManagers.getPatchConstantMap()).thenReturn(map);
  }

  @Test
  void picksTheHighestNumberedPatch() {
    patches(
        PatchConstant.builder().id(50L).name("7.35").date("2024-01-10T00:00:00.000Z").build(),
        PatchConstant.builder().id(52L).name("7.37").date("2024-08-21T20:22:35.000Z").build(),
        PatchConstant.builder().id(51L).name("7.36").date("2024-05-22T00:00:00.000Z").build());

    assertThat(resolver.getCurrentPatchStartDate()).contains(LocalDate.of(2024, 8, 21));
  }

  @Test
  void acceptsABareDate() {
    patches(PatchConstant.builder().id(52L).name("7.37").date("2024-08-21").build());

    assertThat(resolver.getCurrentPatchStartDate()).contains(LocalDate.of(2024, 8, 21));
  }

  @Test
  void unparseableDate_isEmptySoCallersCanSayTheyFellBack() {
    patches(PatchConstant.builder().id(52L).name("7.37").date("whenever").build());

    assertThat(resolver.getCurrentPatchStartDate()).isEmpty();
  }

  @Test
  void nullDate_isEmpty() {
    patches(PatchConstant.builder().id(52L).name("7.37").build());

    assertThat(resolver.getCurrentPatchStartDate()).isEmpty();
  }

  @Test
  void noPatchConstants_isEmpty() {
    Mockito.when(constantsManagers.getPatchConstantMap()).thenReturn(Map.of());

    assertThat(resolver.getCurrentPatchStartDate()).isEmpty();
  }

  @Test
  void constantsFailure_isEmptyRatherThanPropagating() {
    Mockito.when(constantsManagers.getPatchConstantMap())
        .thenThrow(new IllegalStateException("OpenDota down"));

    assertThat(resolver.getCurrentPatchStartDate()).isEmpty();
  }
}
