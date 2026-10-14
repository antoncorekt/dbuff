package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.match.DotaApiLastMatchesService.PlayerLine;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class StageOneCardBuilderTest {

  private final ConstantsManagers constants = Mockito.mock(ConstantsManagers.class);
  private final StageOneCardBuilder builder = new StageOneCardBuilder(constants);

  private void heroes() {
    Mockito.when(constants.getHeroConstantMap())
        .thenReturn(Map.of("14", new HeroConstant("14", "npc_dota_hero_pudge", "Pudge")));
  }

  @Test
  void oneBulletPerTrackedPlayerWithHeroKdaIncomeAndResult() {
    heroes();
    PlayerLine won = new PlayerLine(1, 0L, 14L, 10L, 2L, 5L, 512L, 640L, true);
    PlayerLine lost = new PlayerLine(2, 130L, 14L, 1L, 9L, 3L, 300L, 400L, true);
    MatchFacts facts =
        new MatchFacts(
            1L, null, MatchFacts.Result.ofLines(List.of(won, lost)), null, null, null, null, null);

    SectionView card = builder.build(facts, List.of(won, lost), Map.of(1L, "Tigress", 2L, "Rex"));

    assertThat(card.bullets())
        .containsExactly(
            "Tigress · Pudge · 10/2/5 · 512/640 · Win", "Rex · Pudge · 1/9/3 · 300/400 · Loss");
    assertThat(card.accent()).isEqualTo(Accent.NEUTRAL);
  }

  @Test
  void missingFieldsAndUnknownHeroDoNotBreakTheLine() {
    heroes();
    PlayerLine sparse = new PlayerLine(3, null, 9999L, null, null, null, null, null, null);

    SectionView card = builder.build(facts(), List.of(sparse), Map.of());

    assertThat(card.bullets())
        .containsExactly("Player · unknown hero · ?/?/? · ?/? · result unknown");
  }

  @Test
  void heroNamesThatCannotBeLoadedMeanUnknownHeroes() {
    Mockito.when(constants.getHeroConstantMap()).thenThrow(new IllegalStateException("down"));
    PlayerLine line = new PlayerLine(3, 0L, 14L, 1L, 1L, 1L, 1L, 1L, true);

    assertThat(builder.build(facts(), List.of(line), Map.of(3L, "Ann")).bullets())
        .containsExactly("Ann · unknown hero · 1/1/1 · 1/1 · Win");
  }

  @Test
  void noLinesMeansNoCard() {
    assertThat(builder.build(facts(), List.of(), Map.of())).isNull();
  }

  private static MatchFacts facts() {
    return new MatchFacts(1L, null, new MatchFacts.Result(0, 0), null, null, null, null, null);
  }
}
