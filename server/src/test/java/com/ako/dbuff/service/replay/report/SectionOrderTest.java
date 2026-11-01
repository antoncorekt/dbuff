package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.ai.reasons.AiReasonsSection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class SectionOrderTest {

  private static final List<Class<? extends ReplayReportSection>> SECTIONS =
      List.of(
          DraftSection.class,
          DraftBoardSection.class,
          CompositionSection.class,
          SkillPicksSection.class,
          OpeningSection.class,
          LanesSection.class,
          MidActivitySection.class,
          RotationsSection.class,
          SmokeSection.class,
          ImportantFightsSection.class,
          FightSection.class,
          DeathSection.class,
          VisionSection.class,
          ObjectivesSection.class,
          EndGameSection.class,
          ItemsSection.class,
          GraphAdvantageSection.class,
          GraphIncomeSection.class,
          GraphFarmSection.class,
          MovementSection.class,
          SkillEffectsSection.class,
          OutliersSection.class,
          AiReasonsSection.class,
          OpenDotaSection.class);

  private static final Map<String, Integer> EXPECTED =
      Map.ofEntries(
          Map.entry("draft", 10),
          Map.entry("draft-board", 11),
          Map.entry("composition", 12),
          Map.entry("skill-picks", 13),
          Map.entry("opening", 20),
          Map.entry("lanes", 22),
          Map.entry("mid-activity", 30),
          Map.entry("rotations", 32),
          Map.entry("smoke", 34),
          Map.entry("important-fights", 40),
          Map.entry("fights", 42),
          Map.entry("deaths", 44),
          Map.entry("vision", 46),
          Map.entry("objectives", 48),
          Map.entry("endgame", 50),
          Map.entry("items", 62),
          Map.entry("graph-advantage", 64),
          Map.entry("graph-income", 66),
          Map.entry("graph-farm", 68),
          Map.entry("movement", 70),
          Map.entry("skill-effects", 72),
          Map.entry("outliers", 74),
          Map.entry("ai-reasons", 80),
          Map.entry("opendota", 200));

  private static ReplayReportSection mock(Class<? extends ReplayReportSection> type) {
    return Mockito.mock(type, Mockito.CALLS_REAL_METHODS);
  }

  @Test
  void everySectionHasItsPlaceInTheReport() {
    Map<String, Integer> actual = new TreeMap<>();
    SECTIONS.forEach(type -> actual.put(mock(type).name(), mock(type).order()));
    assertThat(actual).containsExactlyInAnyOrderEntriesOf(EXPECTED);
    assertThat(actual.values()).doesNotHaveDuplicates();
  }

  @Test
  void everySectionSaysInOnePhraseWhatItReports() {
    for (Class<? extends ReplayReportSection> type : SECTIONS) {
      ReplayReportSection section = mock(type);
      assertThat(section.summary()).isNotBlank().isNotEqualTo(section.name()).hasSizeLessThan(61);
    }
  }
}
