package com.ako.dbuff.service.replay.analysis.skill;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.model.SkillEffectDomain;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.ReplayCastSnapshotRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.dao.repo.SkillEffectRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.health.HeroHealth;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.skill.SkillAspect.Outcome;
import com.ako.dbuff.service.replay.analysis.skill.SkillContext.Hero;
import com.ako.dbuff.service.replay.analysis.skill.SkillContext.Match;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SkillEffectAnalyzerTest {
  private static final String SKILL = "oracle_false_promise";
  private static final String FIRST = "npc_dota_hero_sniper";
  private static final String SECOND = "npc_dota_hero_rubick";
  private static final String BYSTANDER = "npc_dota_hero_axe";
  private static final int HEROES = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;

  private static final String CATALOG =
      """
      parameters: { dangerShare: 0.3 }
      entries:
        - key: false_promise
          abilities: [oracle_false_promise]
          aspects: [promise]
      """;

  private static SkillEffectAnalyzer analyzer(
      String yaml, List<SkillAspect> aspects, Set<String> snapshotAbilities) {
    ReplayProcessingProperties processing = new ReplayProcessingProperties();
    processing.setSnapshotAbilities(snapshotAbilities);
    return new SkillEffectAnalyzer(
        new SkillCatalogFile(
            () -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yaml"),
        aspects,
        processing,
        mock(CombatLogReader.class),
        mock(ReplayPlayerRepo.class),
        mock(ReplayPlayerTrackRepo.class),
        mock(ReplayStateChangeRepo.class),
        mock(FightRepo.class),
        mock(ReplayCastSnapshotRepo.class),
        mock(HeroKits.class),
        mock(SkillEffectRepo.class));
  }

  private static CombatEvent cast(double second, String hero) {
    return new CombatEvent(
        CombatEventKind.ABILITY, second, hero, BYSTANDER, SKILL, HEROES, 0, 0, 0, 0, 0, 0, 0);
  }

  private static Hero hero(long slot, String name, List<CombatEvent> events) {
    return new Hero(slot, (int) slot * 2, name, 2, null, new HeroHealth(name, events, null));
  }

  private static Match match(List<CombatEvent> events) {
    return new Match(
        List.of(hero(0, FIRST, events), hero(1, SECOND, events), hero(2, BYSTANDER, events)),
        events,
        List.of(),
        name -> Optional.empty(),
        List::of);
  }

  @Test
  void writesOneRowPerCasterAndNoneForAHeroWithoutTheSkill() {
    List<CombatEvent> events = List.of(cast(100, FIRST), cast(200, FIRST), cast(300, SECOND));
    SkillEffectAnalyzer analyzer = analyzer(CATALOG, List.of(new PromiseAspect()), Set.of(SKILL));

    List<SkillEffectDomain> rows = analyzer.judge(7, match(events), Map.of());

    assertThat(rows)
        .extracting(SkillEffectDomain::getPlayerSlot, SkillEffectDomain::getCastCount)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(0L, 2), org.assertj.core.groups.Tuple.tuple(1L, 1));
    assertThat(rows).allMatch(r -> r.getMatchId() == 7 && "false_promise".equals(r.getEntryKey()));
    assertThat(rows.getFirst().getFigures())
        .isEqualTo("{\"SAVE\":0,\"IMPACT\":0,\"NOT_COUNTED\":2,\"MISSED\":0}");
    assertThat(rows.getFirst().getDetails()).contains("\"outcome\":\"NOT_COUNTED\"");
  }

  @Test
  void aHeroWhoLearnedTheSkillButNeverCastItGetsARowWithZeroCasts() {
    ReplayStateChangeDomain learned =
        ReplayStateChangeDomain.builder()
            .kind(StateChangeKind.ABILITY_LEVEL.name())
            .name(SKILL)
            .gameSeconds(10.0)
            .abilityLevel(1)
            .build();
    SkillEffectAnalyzer analyzer = analyzer(CATALOG, List.of(new PromiseAspect()), Set.of(SKILL));

    List<SkillEffectDomain> rows =
        analyzer.judge(7, match(List.of()), Map.of(2L, List.of(learned)));

    assertThat(rows)
        .extracting(SkillEffectDomain::getPlayerSlot, SkillEffectDomain::getCastCount)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(2L, 0));
  }

  @Test
  void anUnknownAspectOrParameterFailsAtStartWithEveryProblemNamed() {
    String yaml =
        """
        entries:
          - key: a
            abilities: [x]
            aspects: [nothing]
          - key: b
            abilities: [y]
            aspects: [promise]
            parameters: { typo: 1 }
        """;
    SkillEffectAnalyzer analyzer = analyzer(yaml, List.of(new PromiseAspect()), Set.of("x", "y"));

    assertThatThrownBy(analyzer::checkCatalog)
        .hasMessageContaining("entry 'a': unknown aspect 'nothing'")
        .hasMessageContaining("entry 'b': unknown parameter 'typo'");
  }

  @Test
  void anAbilityThatNeedsSnapshotsMustBeRecordedByTheParser() {
    SkillAspect needy =
        new SkillAspect() {
          @Override
          public String name() {
            return "promise";
          }

          @Override
          public Set<String> parameterNames() {
            return Set.of("dangerShare");
          }

          @Override
          public List<String> outcomes() {
            return List.of();
          }

          @Override
          public boolean needsSnapshots() {
            return true;
          }

          @Override
          public List<Outcome> judge(SkillContext context) {
            return List.of();
          }
        };

    assertThatThrownBy(() -> analyzer(CATALOG, List.of(needy), Set.of()).checkCatalog())
        .hasMessageContaining("ability 'oracle_false_promise' needs cast snapshots");
    analyzer(CATALOG, List.of(needy), Set.of(SKILL)).checkCatalog();
  }

  @Test
  void theShippedCatalogIsValidWithTheShippedAspectsAndDefaultProperties() {
    ReplayProcessingProperties processing = new ReplayProcessingProperties();
    new SkillEffectAnalyzer(
            new SkillCatalogFile("replay/skills.yaml"),
            List.of(new PromiseAspect(), new HookAspect(), new TrapAspect()),
            processing,
            mock(CombatLogReader.class),
            mock(ReplayPlayerRepo.class),
            mock(ReplayPlayerTrackRepo.class),
            mock(ReplayStateChangeRepo.class),
            mock(FightRepo.class),
            mock(ReplayCastSnapshotRepo.class),
            mock(HeroKits.class),
            mock(SkillEffectRepo.class))
        .checkCatalog();
  }
}
