package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.activity.ActivitySegment;
import com.ako.dbuff.service.replay.analysis.role.AllPickRoleResolver;
import com.ako.dbuff.service.replay.analysis.role.RoleAssignment;
import com.ako.dbuff.service.replay.analysis.role.RolePlayer;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Derives the roles of two real All Pick matches from the replay's activity and OpenDota's stored
 * statistics and compares them with what OpenDota says about the lanes and the net worth.
 */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class PlayerRoleReplayTest {

  private static final int TEAM_SIZE = 5;
  private final AllPickRoleResolver resolver =
      new AllPickRoleResolver(new ReplayAnalysisProperties());

  @Test
  void rolesOfTwoAllPickMatchesFollowTheLanesAndTheNetWorth() throws IOException {
    for (String match : List.of("9040892342", "9040889566")) {
      Path dir = Path.of(System.getenv("REPLAY_FIXTURE_DIR"));
      ReplayFacts facts =
          Parsers.standard().parse(dir.resolve(match + ".dem"), TestModes.ALL_PICK, () -> false);
      Map<Long, List<ActivitySegment>> activity = new HashMap<>();
      ReplayActivities.of(facts).forEach((p, segments) -> activity.put(p.playerSlot(), segments));
      JsonNode json = new ObjectMapper().readTree(dir.resolve(match + ".json").toFile());
      Map<Long, JsonNode> openDota = new HashMap<>();
      json.get("players").forEach(p -> openDota.put(p.get("player_slot").asLong(), p));

      for (boolean dire : new boolean[] {false, true}) {
        List<RolePlayer> team =
            openDota.keySet().stream()
                .filter(slot -> (slot >= 128) == dire)
                .sorted()
                .map(slot -> rolePlayer(openDota.get(slot), activity.get(slot)))
                .toList();
        List<RoleAssignment> result = resolver.resolve(team);

        assertThat(result)
            .extracting(RoleAssignment::role)
            .containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        report(match, dire, openDota, result);
        assertSensible(openDota, result);
      }
    }
  }

  private static RolePlayer rolePlayer(JsonNode p, List<ActivitySegment> activity) {
    double[] series =
        StreamSupport.stream(p.get("networth_t").spliterator(), false)
            .mapToDouble(JsonNode::asDouble)
            .toArray();
    return new RolePlayer(
        p.get("player_slot").asLong(),
        p.get("lane_role").asLong(),
        p.get("net_worth").asLong(),
        series,
        activity);
  }

  private static void report(
      String match, boolean dire, Map<Long, JsonNode> openDota, List<RoleAssignment> result) {
    System.out.println("ROLES " + match + (dire ? " dire" : " radiant"));
    for (RoleAssignment a :
        result.stream().sorted(Comparator.comparing(RoleAssignment::role)).toList()) {
      JsonNode p = openDota.get(a.slot());
      System.out.printf(
          "  role %d slot %3d hero %3d lane_role %d roaming %-5s net_worth %6d confidence %.2f |"
              + " %s%n",
          a.role(),
          a.slot(),
          p.get("hero_id").asInt(),
          p.get("lane_role").asInt(),
          p.get("is_roaming").asBoolean(),
          p.get("net_worth").asLong(),
          a.confidence(),
          a.evidence());
    }
  }

  private static void assertSensible(Map<Long, JsonNode> openDota, List<RoleAssignment> team) {
    Map<Long, Integer> roles = new HashMap<>();
    team.forEach(a -> roles.put(a.slot(), a.role()));
    List<Long> byWorth = new ArrayList<>(roles.keySet());
    byWorth.sort(
        Comparator.comparing((Long s) -> openDota.get(s).get("net_worth").asLong()).reversed());
    assertThat(roles.get(byWorth.getFirst()))
        .as("the richest player is a core")
        .isLessThanOrEqualTo(3);
    assertThat(roles.get(byWorth.getLast()))
        .as("the poorest player is a support")
        .isGreaterThanOrEqualTo(4);
    List<Long> mid =
        roles.keySet().stream()
            .filter(s -> openDota.get(s).get("lane_role").asInt() == 2)
            .filter(s -> !openDota.get(s).get("is_roaming").asBoolean())
            .toList();
    if (mid.size() == 1) {
      assertThat(roles.get(mid.getFirst())).as("the only mid laner plays mid").isEqualTo(2);
    }
  }
}
