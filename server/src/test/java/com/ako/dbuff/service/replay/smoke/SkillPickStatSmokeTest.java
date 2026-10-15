package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.SkillPickStatRepo;
import com.ako.dbuff.resources.model.SkillStatResponse;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.HeroesAbilityConstant;
import com.ako.dbuff.service.discord.command.FakeCommandContext;
import com.ako.dbuff.service.discord.command.SkillNameIndex;
import com.ako.dbuff.service.discord.command.impl.SkillsCommand;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillLookup;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillPickStatAnalyzer;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatKind;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatNames;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatProperties;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatReader;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatWording;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import com.ako.dbuff.service.replay.report.AbilityLabels;
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
  @Autowired private SkillLookup skillLookup;
  @Autowired private SkillStatWording wording;
  @Autowired private SkillStatProperties skillStatProperties;
  @Autowired private AbilityLabels abilityLabels;
  private SkillsCommand skillsCommand;
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

  /**
   * Runs the real command against the fixture history (every fixture game counts, as the command
   * reads "now minus 12 months" and has no match to leave out) and writes what it posts.
   */
  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void theSkillsCommandAnswersFromTheFixtureHistoryAndTheAnswersAreWritten() throws IOException {
    long sample = 0;
    for (String file : fixtureIds()) {
      if (seedHistory(file) && sample == 0) {
        sample = Long.parseLong(file);
      }
    }
    PlayerMatchStatisticDomain player = playerStatRepo.findAllByMatchId(sample).get(0);
    List<Long> held =
        abilityRepo.findAllByMatchId(sample).stream()
            .filter(a -> a.getPlayerSlot().equals(player.getPlayerSlot()))
            .map(AbilityDomain::getAbilityId)
            .distinct()
            .toList();
    skillsCommand = commandOver(player.getHeroId(), held);
    String hero = names.hero(player.getHeroId());
    String first = names.skill(held.get(0));
    String second = names.skill(held.get(1));
    List<String> out = new ArrayList<>();
    out.add("# /skills sample output");
    out.add("");
    out.add(
        "Real SkillsCommand, SkillLookup, SkillCountRepository and wording on a real PostgreSQL"
            + " seeded with the Ability Draft fixture replays (every fixture game counts as"
            + " history; nothing is left out). Hero and skill names come from the fixture"
            + " constants; the fixtures hold no hero_abilities constant, so the hero's skill list"
            + " is the skills the sampled player held in the fixture.");
    ask(out, "/skills hero:" + hero, "hero", hero);
    ask(out, "/skills skill:" + first, "skill", first);
    ask(out, "/skills skill:" + first + " with:" + second, "skill", first, "with", second);
    ask(out, "/skills skill:" + second + " with:" + first, "skill", second, "with", first);
    ask(out, "/skills hero:Nobody Atall", "hero", "Nobody Atall");
    ask(out, "/skills with:" + second, "with", second);
    ask(out, "/skills", "x", "y");
    Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("skills-command.md"), String.join("\n", out) + "\n");
    assertThat(out).anyMatch(line -> line.contains("in only 1 game") || line.contains(" games"));
  }

  private SkillsCommand commandOver(long heroId, List<Long> heroSkills) throws IOException {
    ObjectMapper lenient =
        new ObjectMapper()
            .configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
    Map<String, HeroConstant> heroes = new HashMap<>();
    lenient
        .readTree(fixture("constants-heroes.json").toFile())
        .fields()
        .forEachRemaining(
            e -> heroes.put(e.getKey(), lenient.convertValue(e.getValue(), HeroConstant.class)));
    Map<String, AbilityIdsConstant> abilities = new HashMap<>();
    abilityNamesById()
        .forEach(
            (id, name) ->
                abilities.put(
                    id, AbilityIdsConstant.builder().id(Long.parseLong(id)).name(name).build()));
    ConstantsManagers constants = org.mockito.Mockito.mock(ConstantsManagers.class);
    org.mockito.Mockito.when(constants.getHeroConstantMap()).thenReturn(heroes);
    org.mockito.Mockito.when(constants.getAbilityConstantMap()).thenReturn(abilities);
    org.mockito.Mockito.when(constants.getHeroAbilitiesMap())
        .thenReturn(
            Map.of(
                heroes.get(String.valueOf(heroId)).getName(),
                HeroesAbilityConstant.builder()
                    .abilities(
                        heroSkills.stream()
                            .map(id -> abilities.get(String.valueOf(id)).getName())
                            .toList())
                    .build()));
    return new SkillsCommand(
        new ConstantNameResolver(constants),
        new SkillNameIndex(constants, abilityLabels),
        skillLookup,
        wording,
        names,
        skillStatProperties);
  }

  private void ask(List<String> out, String typed, String... options) {
    FakeCommandContext.Builder builder = FakeCommandContext.builder();
    for (int i = 0; i < options.length; i += 2) {
      builder.option(options[i], options[i + 1]);
    }
    FakeCommandContext context = builder.build();
    skillsCommand.execute(null, context);
    out.add("");
    out.add("## " + typed);
    out.add("");
    out.add("```");
    out.addAll(context.getEphemeralReplies());
    out.addAll(context.getPosts());
    out.addAll(context.getFailures());
    out.add("```");
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
