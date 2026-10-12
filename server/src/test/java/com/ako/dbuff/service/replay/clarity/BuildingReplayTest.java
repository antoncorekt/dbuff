package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.ReplayFacts;
import com.ako.dbuff.service.replay.analysis.building.BuildingName;
import com.ako.dbuff.service.replay.map.MapLayout;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** The buildings the combat log reports as killed against OpenDota's building_kill objectives. */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
class BuildingReplayTest {

  @Test
  void everyBuildingOpenDotaCountsIsFoundAndEachIsNamedByTheMap() throws Exception {
    for (String match : new String[] {"9040892342", "9040889566"}) {
      Path dir = Path.of(System.getenv("REPLAY_FIXTURE_DIR"));
      ReplayFacts facts =
          new ClarityReplayParser()
              .parse(dir.resolve(match + ".dem"), TestModes.ALL_PICK, () -> false);
      JsonNode objectives =
          new ObjectMapper().readTree(dir.resolve(match + ".json").toFile()).get("objectives");
      long openDota = 0;
      for (JsonNode o : objectives) {
        if (o.get("type").asText().equals("building_kill")) {
          openDota++;
        }
      }

      var layout = new MapLayout(facts.map(), MapLayout.Tuning.defaults());
      var kills =
          facts.combatLog().events().stream()
              .filter(e -> e.kind() == CombatEventKind.BUILDING_KILL)
              .filter(e -> BuildingName.parse(e.target()).isPresent())
              .toList();

      assertThat((long) kills.size()).as(match).isEqualTo(openDota);
      assertThat(kills)
          .as(match)
          .allSatisfy(
              k -> assertThat(layout.buildingPosition(k.target())).as(k.target()).isPresent());
    }
  }
}
