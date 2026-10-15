package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.discord.icon.TestIcons;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiMatchFactsBuilderTest {

  private static List<String> lines(List<Fact> facts) {
    return facts.stream().map(Fact::line).toList();
  }

  @Test
  void buildsLabelledFactsWithStableIdsFromTheMatchAndTheSections() {
    List<Fact> facts =
        ReasonsFixtures.builder(
                ReasonsFixtures.section(
                    "important-fights",
                    "Dire (your team) won 2 of 2 important fights",
                    "**8:10** Dire (your team) won the fight, 4.1k net worth swing",
                    "22:40 Dire (your team) won the fight, took the lead"),
                ReasonsFixtures.section("lanes", null, "Bottom lane: Dire won clearly"),
                ReasonsFixtures.section("smoke", null))
            .build(ReasonsFixtures.input());

    assertThat(lines(facts))
        .containsExactly(
            "RESULT: Dire won after 34:12. Final kills: Radiant 20, Dire 31. Your team is Dire and"
                + " it won.",
            "DRAFT_RADIANT: Heroes of Radiant: Axe.",
            "DRAFT_DIRE: Heroes of Dire (your team): Faceless Void, Crystal Maiden.",
            "STATS_AXE: Axe, Radiant: 4/9/2 K/D/A, 380 GPM, 410 XPM, net worth 15.2k.",
            "STATS_FACELESS_VOID: Faceless Void, Dire (your team): 11/3/14 K/D/A, 612 GPM, 700 XPM,"
                + " net worth 31.8k.",
            "STATS_CRYSTAL_MAIDEN: Crystal Maiden, Dire (your team): 2/6/20 K/D/A, 301 GPM, 420"
                + " XPM, net worth 14.1k.",
            "ROLE_FACELESS_VOID: Faceless Void, Dire (your team), played position 1 (carry).",
            "ROLE_CRYSTAL_MAIDEN: Crystal Maiden, Dire (your team), played position 5 (hard"
                + " support).",
            "F1: Dire (your team) won 2 of 2 important fights",
            "F2: 8:10 Dire (your team) won the fight, 4.1k net worth swing",
            "F3: 22:40 Dire (your team) won the fight, took the lead",
            "LANE_1: Bottom lane: Dire won clearly");
  }

  @Test
  void sectionTextWithIconsReachesTheModelWithoutThem() {
    List<Fact> facts =
        ReasonsFixtures.builder(
                ReasonsFixtures.section(
                    "important-fights",
                    TestIcons.heroMention()
                        + "Axe had "
                        + TestIcons.abilityMention()
                        + "Blink ready",
                    "⚔️ Axe bought " + TestIcons.itemMention() + "Blink Dagger"))
            .build(ReasonsFixtures.input());

    assertThat(lines(facts))
        .contains("F1: Axe had Blink ready", "F2: Axe bought Blink Dagger")
        .noneMatch(line -> line.contains("<:") || line.contains("⚔️"));
  }

  @Test
  void idsAreStableAcrossRuns_andEveryIdBelongsToAKind() {
    var builder = ReasonsFixtures.builder(ReasonsFixtures.section("lanes", null, "one", "two"));
    List<Fact> first = builder.build(ReasonsFixtures.input());
    List<Fact> second = builder.build(ReasonsFixtures.input());

    assertThat(first).isEqualTo(second);
    assertThat(first).allSatisfy(f -> assertThat(FactKind.ofId(f.id())).contains(f.kind()));
  }

  @Test
  void missingDataMeansAbsentFacts_neverAnError() {
    MatchDomain bare = MatchDomain.builder().id(ReasonsFixtures.MATCH_ID).build();
    ReplayReportInput input =
        new ReplayReportInput(ReasonsFixtures.MATCH_ID, bare, Set.of(), Map.of(), 3);

    List<Fact> facts = ReasonsFixtures.builder().build(input);

    assertThat(facts).noneMatch(f -> f.kind() == FactKind.RESULT);
    assertThat(facts).anyMatch(f -> f.kind() == FactKind.STATS);
  }

  @Test
  void aSectionThatFailsLeavesOutItsFactsOnly() {
    var broken =
        new com.ako.dbuff.service.replay.report.ReplayReportSection() {
          @Override
          public String name() {
            return "lanes";
          }

          @Override
          public int order() {
            return 1;
          }

          @Override
          public com.ako.dbuff.dao.model.ReportDetail minimumDetail() {
            return com.ako.dbuff.dao.model.ReportDetail.STANDARD;
          }

          @Override
          public List<String> lines(ReplayReportInput input) {
            throw new IllegalStateException("boom");
          }

          @Override
          public java.util.Optional<com.ako.dbuff.service.replay.report.view.SectionView> view(
              ReplayReportInput input) {
            throw new IllegalStateException("boom");
          }
        };

    List<Fact> facts = ReasonsFixtures.builder(broken).build(ReasonsFixtures.input());

    assertThat(facts).anyMatch(f -> f.kind() == FactKind.RESULT);
    assertThat(facts).noneMatch(f -> f.kind() == FactKind.LANE);
  }

  @Test
  void factsHoldNoMarkupAndNoNames() {
    List<Fact> facts =
        ReasonsFixtures.builder(
                ReasonsFixtures.section("endgame", "**Throw**", "line one\nline two with `code`"))
            .build(
                new ReplayReportInput(
                    ReasonsFixtures.MATCH_ID,
                    ReasonsFixtures.match(),
                    Set.of(128L),
                    Map.of(128L, "SomeDiscordName"),
                    3));

    assertThat(lines(facts)).contains("ENDGAME_1: Throw", "ENDGAME_2: line one line two with code");
    assertThat(String.join("\n", lines(facts)))
        .doesNotContain("SomeDiscordName")
        .doesNotContain("*");
  }
}
