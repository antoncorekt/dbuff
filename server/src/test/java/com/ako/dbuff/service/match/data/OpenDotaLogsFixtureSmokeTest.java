package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.model.WardKind;
import com.ako.dbuff.dao.repo.RuneEventRepo;
import com.ako.dbuff.dao.repo.UnitKillRepo;
import com.ako.dbuff.dao.repo.WardEventRepo;
import com.ako.dbuff.dotapi.invoker.JSON;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.service.replay.smoke.LocalDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stores the real OpenDota JSON of the fixture matches in a real PostgreSQL and checks that the
 * stored rows are exactly the entries of the logs. Needs {@code REPLAY_FIXTURE_DIR} and {@code
 * SMOKE_PG_URL} (user postgres, password password).
 */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "SMOKE_PG_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
@DataJpaTest(
    showSql = false,
    properties = {
      "spring.datasource.url=${SMOKE_PG_URL}",
      "spring.datasource.username=postgres",
      "spring.datasource.password=password",
      "spring.datasource.driver-class-name=org.postgresql.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "spring.jpa.hibernate.ddl-auto=update",
      "spring.jpa.show-sql=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  MatchDetailService.class,
  RuneEventMapper.class,
  WardEventMapper.class,
  UnitKillMapper.class
})
@ExtendWith(LocalDatabase.class)
class OpenDotaLogsFixtureSmokeTest {

  @Autowired private MatchDetailService service;
  @Autowired private RuneEventRepo runeRepo;
  @Autowired private WardEventRepo wardRepo;
  @Autowired private UnitKillRepo killRepo;

  @ParameterizedTest
  @ValueSource(longs = {9037038717L, 9038725154L, 9040889566L, 9040892342L})
  void storedRowCountsEqualTheLogEntries(long id) throws IOException {
    Path file = Path.of(System.getenv("REPLAY_FIXTURE_DIR"), id + ".json");
    JsonNode json = new ObjectMapper().readTree(file.toFile());
    MatchResponse response = new JSON().getMapper().readValue(file.toFile(), MatchResponse.class);

    service.replace(response, MatchDomain.builder().id(id).build());

    assertThat(runeRepo.findByMatchId(id)).hasSize(total(json, "runes_log"));
    List<WardEventDomain> wards = wardRepo.findByMatchId(id);
    assertThat(wards).hasSize(total(json, "obs_log") + total(json, "sen_log"));
    assertThat(wards.stream().filter(w -> w.getWardKind() == WardKind.OBSERVER).count())
        .isEqualTo(total(json, "obs_log"));
    assertThat(wards.stream().filter(w -> w.getRemovedTime() != null).count())
        .as("every removal finds its placement")
        .isEqualTo(total(json, "obs_left_log") + total(json, "sen_left_log"));
    assertThat(killRepo.findByMatchId(id)).hasSize(total(json, "killed"));
    System.out.printf(
        "OPENDOTA LOG ROWS %d: rune_event=%d ward_event=%d unit_kill=%d%n",
        id, runeRepo.findByMatchId(id).size(), wards.size(), killRepo.findByMatchId(id).size());
  }

  private static int total(JsonNode json, String field) {
    return StreamSupport.stream(json.get("players").spliterator(), false)
        .mapToInt(player -> player.path(field).size())
        .sum();
  }
}
