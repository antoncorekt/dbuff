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
 * aspect the analyzer has never heard of: it is judged by aspect beans added here, and nothing in
 * the analyzer changes. A third entry lists two aspects, and its row is their sum.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({
  ReplayItemEfficiencyAnalyzer.class,
  ItemEfficiencyInputs.class,
  ChargeRestoreAspect.class,
  SaveAspect.class,
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
        .containsExactlyInAnyOrder("magic_stick", "toy", "summed");
    ItemEfficiencyDomain stick =
        rows.stream().filter(r -> r.getEntryKey().equals("magic_stick")).findFirst().orElseThrow();
    assertThat(stick.getPlayerSlot()).isZero();
    assertThat(stick.getItemKey()).isEqualTo("magic_stick");
    assertThat(stick.getKind()).isEqualTo("charge-restore");
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
  void analyze_asksTheToyAspectForItsEntryWithItsParameters() {
    stored();

    analyzer.analyze(MATCH);

    ItemEfficiencyDomain toy =
        repo.findByMatchId(MATCH).stream()
            .filter(r -> r.getEntryKey().equals("toy"))
            .findFirst()
            .orElseThrow();
    assertThat(toy.getKind()).isEqualTo(ToyAspect.NAME);
    assertThat(toy.getGain()).isEqualTo(7.0);
    assertThat(toy.getItemKey()).isEqualTo("quelling_blade");
  }

  @Test
  void analyze_sumsThePartsOfTheAspectsOfOneEntry() {
    stored();

    analyzer.analyze(MATCH);

    ItemEfficiencyDomain summed =
        repo.findByMatchId(MATCH).stream()
            .filter(r -> r.getEntryKey().equals("summed"))
            .findFirst()
            .orElseThrow();
    assertThat(summed.getKind()).isEqualTo(ToyAspect.NAME);
    assertThat(summed.getGain()).isEqualTo(7.0 + 2.0);
    assertThat(summed.getRatio()).isEqualTo(9.0 / 100);
    ItemEfficiency back = ItemEfficiencyJson.fromRow(summed);
    assertThat(back.figures()).containsEntry("blades", 3.0);
    assertThat(back.rows()).hasSize(1);
    assertThat(back.rows().getFirst().figures())
        .containsEntry("weight", 7.0)
        .containsEntry("bonus", 2.0);
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

  /** An aspect the analyzer does not know: its gain is its weight. */
  static class ToyAspect implements ItemAspect {
    static final String NAME = "toy";

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public Set<String> parameterNames() {
      return Set.of("weight");
    }

    @Override
    public AspectPart measure(AspectContext context) {
      double weight = context.entry().parameter("weight", 1.0);
      return new AspectPart(
          weight,
          true,
          Map.of("blades", 1.0),
          List.of(),
          List.of(new ItemEfficiency.Detail("TOY", 1.0, null, Map.of("weight", weight))),
          1,
          null,
          null);
    }
  }

  /** A second aspect, to be summed with the first. */
  static class ToyBonusAspect implements ItemAspect {
    @Override
    public String name() {
      return "toy-bonus";
    }

    @Override
    public Set<String> parameterNames() {
      return Set.of("bonus");
    }

    @Override
    public AspectPart measure(AspectContext context) {
      double bonus = context.entry().parameter("bonus", 0.0);
      return new AspectPart(
          bonus,
          true,
          Map.of("blades", 2.0),
          List.of(),
          List.of(new ItemEfficiency.Detail("TOY", 1.0, null, Map.of("bonus", bonus))),
          0,
          null,
          null);
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
    ToyAspect toyAspect() {
      return new ToyAspect();
    }

    @Bean
    ToyBonusAspect toyBonusAspect() {
      return new ToyBonusAspect();
    }

    @Bean
    ItemEfficiencyFile itemEfficiencyFile() {
      String json =
          "{\"categories\": {\"farming\": \"F\"}, \"entries\": [{\"key\": \"magic_stick\","
              + " \"category\": \"farming\", \"aspects\": [\"charge-restore\", \"save\"],"
              + " \"items\": [\"magic_stick\", \"magic_wand\"]},{\"key\": \"toy\","
              + " \"category\": \"farming\", \"aspects\": [\"toy\"], \"items\":"
              + " [\"quelling_blade\"], \"parameters\": {\"weight\": 7}},{\"key\": \"summed\","
              + " \"category\": \"farming\", \"aspects\": [\"toy\", \"toy-bonus\"],"
              + " \"items\": [\"quelling_blade\"], \"parameters\": {\"weight\": 7, \"bonus\":"
              + " 2}}]}";
      return new ItemEfficiencyFile(
          () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test.yaml");
    }
  }
}
