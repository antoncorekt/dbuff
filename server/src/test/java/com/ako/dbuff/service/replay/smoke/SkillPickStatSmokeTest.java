package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.SkillPickStatRepo;
import com.ako.dbuff.resources.model.SkillStatResponse;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillPickStatAnalyzer;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatKind;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatNames;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatReader;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the skill statistics analyzer over the Ability Draft fixtures (all of them are stored as
 * each other's history, as OpenDota's ability upgrades would have been) and writes what it stored
 * for the four fixture matches in words to {@code ~/dbuff-fixtures/send/skill-pick-stats.md}.
 */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "SMOKE_PG_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
@ReplayPipelineTest
class SkillPickStatSmokeTest extends ReplayPipelineFixture {
  private static final String[] REPORTED = {"9038725154", "9037038717", "9040892342", "9040889566"};
  private static final String LATEST = "9044294412";

  @Autowired private SkillPickStatAnalyzer analyzer;
  @Autowired private SkillStatReader reader;
  @Autowired private SkillStatNames names;
  @Autowired private SkillPickStatRepo statRepo;
  @Autowired private AbilityRepo abilityRepo;
  @Autowired private PlayerGameStatisticRepo playerStatRepo;

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void theFixtureMatchesGetTheirStatisticsAndTheyAreWrittenInWords() throws IOException {
    List<String> abilityDrafts = new ArrayList<>();
    for (String file : fixtureIds()) {
      if (seedHistory(file)) {
        abilityDrafts.add(file);
      }
    }
    HeapProbe heap = new HeapProbe();
    long liveBefore = HeapProbe.liveMbAfterGc();
    heap.resetPeak();
    List<String> out = new ArrayList<>();
    out.add("# Skill pick statistics, four fixture matches");
    out.add("");
    out.add(
        "Counts are the Ability Draft games of the "
            + "12 months before each match, in this fixture set only, the match itself left out.");
    for (String id : concat(REPORTED, LATEST)) {
      long matchId = Long.parseLong(id);
      out.add("");
      out.add(
          "## "
              + id
              + (LATEST.equals(id) ? " (latest Ability Draft fixture, all others before it)" : ""));
      MatchDomain match = matchRepo.findById(matchId).orElseThrow();
      if (!analyzer.supports(profileOf(match))) {
        assertThat(statRepo.findByMatchId(matchId)).isEmpty();
        out.add("Not an Ability Draft game: the analyzer does not apply and stores nothing.");
        continue;
      }
      analyzer.analyze(matchId);
      SkillStatResponse response = reader.match(matchId);
      assertThat(response.players()).hasSize(10);
      assertThat(statRepo.findByMatchId(matchId)).hasSizeGreaterThanOrEqualTo(10 * 5);
      for (SkillStatResponse.PlayerStats player : response.players()) {
        out.add("");
        out.add("### " + names.hero(player.heroId()) + " (slot " + player.playerSlot() + ")");
        for (SkillStatKind kind : SkillStatKind.values()) {
          player.stats().stream()
              .filter(s -> s.kind() == kind && s.games() > 0)
              .forEach(s -> out.add("- " + s.text()));
        }
        long unseen = player.stats().stream().filter(s -> s.games() == 0).count();
        out.add(
            "- "
                + unseen
                + " of "
                + player.stats().size()
                + " figures have no games in the window");
      }
      analyzer.analyze(matchId);
      assertThat(reader.match(matchId)).as("a second run replaces the first").isEqualTo(response);
    }
    System.out.println(
        "HEAP skill stats: live before "
            + liveBefore
            + " MB, peak of all pools during the four reports "
            + heap.peakMb()
            + " MB (old generation "
            + heap.peakOldMb()
            + " MB)");
    assertThat(abilityDrafts).contains(LATEST);
    Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("skill-pick-stats.md"), String.join("\n", out) + "\n");
  }

  private static com.ako.dbuff.service.replay.mode.GameModeProfile profileOf(MatchDomain match) {
    return match.getGameModeId() == AbilityDraftProfile.MODE_ID
        ? new AbilityDraftProfile()
        : new AllPickProfile();
  }

  private static List<String> fixtureIds() throws IOException {
    try (var files = Files.list(Path.of(System.getenv("REPLAY_FIXTURE_DIR")))) {
      return files
          .map(p -> p.getFileName().toString())
          .filter(n -> n.matches("\\d+\\.json"))
          .map(n -> n.substring(0, n.length() - ".json".length()))
          .sorted()
          .toList();
    }
  }

  private static String[] concat(String[] ids, String last) {
    String[] all = java.util.Arrays.copyOf(ids, ids.length + 1);
    all[ids.length] = last;
    return all;
  }

  /** The match, its players' wins and the skills they levelled; false for a non Ability Draft. */
  private boolean seedHistory(String id) throws IOException {
    MatchDomain match = seedMatch(id);
    JsonNode json = new ObjectMapper().readTree(fixture(id + ".json").toFile());
    boolean radiantWin = json.path("radiant_win").asBoolean();
    for (PlayerMatchStatisticDomain stat : playerStatRepo.findAllByMatchId(match.getId())) {
      stat.setWin(radiantWin == (stat.getPlayerSlot() < 128) ? 1L : 0L);
      playerStatRepo.save(stat);
    }
    abilityRepo.deleteByMatchId(match.getId());
    Map<String, String> abilityNames = abilityNamesById();
    for (JsonNode p : json.path("players")) {
      java.util.Set<Long> upgrades = new java.util.TreeSet<>();
      p.path("ability_upgrades_arr").forEach(n -> upgrades.add(n.asLong()));
      for (long upgrade : upgrades) {
        String name = abilityNames.getOrDefault(String.valueOf(upgrade), "");
        if (!name.isEmpty()
            && !name.contains("special_bonus")
            && !name.startsWith("attribute_bonus")) {
          abilityRepo.save(
              AbilityDomain.builder()
                  .matchId(match.getId())
                  .playerSlot(p.path("player_slot").asLong())
                  .playerId(p.path("account_id").asLong(-1))
                  .abilityId(upgrade)
                  .name(name)
                  .build());
        }
      }
    }
    return match.getGameModeId() == AbilityDraftProfile.MODE_ID;
  }

  private static Map<String, String> abilityNamesById() throws IOException {
    Map<String, String> names = new HashMap<>();
    new ObjectMapper()
        .readTree(fixture("constants-ability-ids.json").toFile())
        .fields()
        .forEachRemaining(
            e -> {
              for (String id : e.getKey().split(",")) {
                names.put(id.trim(), e.getValue().asText());
              }
            });
    return names;
  }
}
