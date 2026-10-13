package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ItemEfficiencyDomain;
import com.ako.dbuff.dao.model.ReplayCombatLogDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.activity.StoredActivity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole analyzer over stored rows, with a file that lists a Magic Stick entry and an entry of a
 * kind the analyzer has never heard of: the second is judged by an evaluator bean added here, and
 * nothing in the analyzer changes.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({
  ReplayItemEfficiencyAnalyzer.class,
  ItemEfficiencyInputs.class,
  MagicStickEvaluator.class,
  CombatLogReader.class,
  StoredActivity.class,
  ReplayItemEfficiencyAnalyzerTest.Config.class
})
class ReplayItemEfficiencyAnalyzerTest {
  private static final long MATCH = 9L;
  private static final String HERO = "npc_dota_hero_sniper";

  @Autowired private ReplayItemEfficiencyAnalyzer analyzer;
  @Autowired private ItemEfficiencyRepo repo;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayStateChangeRepo stateRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;

  @Test
  void analyze_storesOneRowPerEntryAndHero_andReplacesAnEarlierRun() {
    stored();

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<ItemEfficiencyDomain> rows = repo.findByMatchId(MATCH);
    assertThat(rows)
        .extracting(ItemEfficiencyDomain::getEntryKey)
        .containsExactlyInAnyOrder("magic_stick", "toy");
    ItemEfficiencyDomain stick =
        rows.stream().filter(r -> r.getEntryKey().equals("magic_stick")).findFirst().orElseThrow();
    assertThat(stick.getPlayerSlot()).isZero();
    assertThat(stick.getItemKey()).isEqualTo("magic_stick");
    assertThat(stick.getKind()).isEqualTo("magic-stick");
    assertThat(stick.getUseCount()).isEqualTo(1);
    assertThat(stick.getCost()).isEqualTo(200);
    assertThat(stick.getPurchaseSeconds()).isEqualTo(-60.0);
    assertThat(stick.getAnalysisVersion()).isEqualTo(1);
    assertThat(stick.getFigures()).contains("\"charges\":4.0");
    ItemEfficiency back = ItemEfficiencyJson.fromRow(stick);
    assertThat(back.rows()).hasSize(1);
    assertThat(back.rows().getFirst().figures()).containsEntry("charges", 4.0);
    assertThat(back.flags()).containsExactly("HP_UNCAPPED", "MANA_UNCAPPED");
  }

  @Test
  void analyze_asksTheToyEvaluatorForItsEntryWithItsParameters() {
    stored();

    analyzer.analyze(MATCH);

    ItemEfficiencyDomain toy =
        repo.findByMatchId(MATCH).stream()
            .filter(r -> r.getEntryKey().equals("toy"))
            .findFirst()
            .orElseThrow();
    assertThat(toy.getKind()).isEqualTo(ToyEvaluator.KIND);
    assertThat(toy.getGain()).isEqualTo(7.0);
    assertThat(toy.getItemKey()).isEqualTo("quelling_blade");
  }

  @Test
  void analyze_storesNothingForAMatchWhoseHeroesHeldNoListedItem() {
    playerRepo.saveAndFlush(player());

    analyzer.analyze(MATCH);

    assertThat(repo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void deleteByMatchId_removesTheRows() {
    stored();
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(repo.findByMatchId(MATCH)).isEmpty();
  }

  private static ReplayPlayerDomain player() {
    return ReplayPlayerDomain.builder()
        .matchId(MATCH)
        .playerSlot(0L)
        .replayPlayerId(0)
        .teamNum(2)
        .heroEntityName("Sniper")
        .build();
  }

  private void stored() {
    playerRepo.saveAndFlush(player());
    stateRepo.saveAndFlush(state(-60, StateChangeKind.ITEM_ADDED, "item_magic_stick", null));
    stateRepo.saveAndFlush(state(10, StateChangeKind.ITEM_CHARGES, "item_magic_stick", 4));
    stateRepo.saveAndFlush(state(20, StateChangeKind.ITEM_ADDED, "item_quelling_blade", null));
    List<CombatEvent> events =
        List.of(
            new CombatEvent(
                com.ako.dbuff.service.replay.CombatEventKind.ITEM,
                30,
                HERO,
                "",
                "item_magic_stick",
                CombatEvent.ATTACKER_HERO,
                0,
                0,
                0,
                0,
                0,
                0,
                0));
    combatLogRepo.saveAndFlush(
        ReplayCombatLogDomain.builder()
            .matchId(MATCH)
            .codecVersion(CombatLogCodec.VERSION)
            .eventCount(events.size())
            .data(CombatLogCodec.encode(events))
            .build());
  }

  private static ReplayStateChangeDomain state(
      double second, StateChangeKind kind, String name, Integer charges) {
    return ReplayStateChangeDomain.builder()
        .matchId(MATCH)
        .playerSlot(0L)
        .gameSeconds(second)
        .kind(kind.name())
        .name(name)
        .slotIndex(0)
        .charges(charges)
        .build();
  }

  /** A kind the analyzer does not know: it counts the hero's quelling blades as its gain. */
  static class ToyEvaluator implements ItemEfficiencyEvaluator {
    static final String KIND = "toy";

    @Override
    public boolean supports(ItemEfficiencyEntry entry) {
      return KIND.equals(entry.kind());
    }

    @Override
    public Set<String> parameterNames() {
      return Set.of("weight");
    }

    @Override
    public List<ItemEfficiency> evaluate(EntryContext context) {
      return context.heroes().stream()
          .filter(h -> !h.held(context.replayNames()).isEmpty())
          .map(
              h ->
                  new ItemEfficiency(
                      h.slot(),
                      context.entry().key(),
                      "quelling_blade",
                      KIND,
                      null,
                      1,
                      context.entry().parameter("weight", 1.0),
                      context.cost(h.held(context.replayNames())),
                      null,
                      null,
                      Map.of(),
                      List.of(),
                      List.of()))
          .toList();
    }
  }

  @TestConfiguration
  static class Config {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    ItemKits itemKits() {
      return ItemTestData.kits(
          Map.of("magic_stick", 200L, "magic_wand", 460L, "quelling_blade", 100L));
    }

    @Bean
    ToyEvaluator toyEvaluator() {
      return new ToyEvaluator();
    }

    @Bean
    ItemEfficiencyFile itemEfficiencyFile() {
      String json =
          "{\"status\": \"test\", \"entries\": ["
              + "{\"key\": \"magic_stick\", \"kind\": \"magic-stick\","
              + " \"items\": [\"magic_stick\", \"magic_wand\"]},"
              + "{\"key\": \"toy\", \"kind\": \"toy\", \"items\": [\"quelling_blade\"],"
              + " \"parameters\": {\"weight\": 7}}]}";
      return new ItemEfficiencyFile(
          () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test.json");
    }
  }
}
