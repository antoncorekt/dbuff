package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.RuneEventDomain;
import com.ako.dbuff.dao.model.UnitKillDomain;
import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.model.WardKind;
import com.ako.dbuff.dao.model.WardRemovalKind;
import com.ako.dbuff.dao.repo.RuneEventRepo;
import com.ako.dbuff.dao.repo.UnitKillRepo;
import com.ako.dbuff.dao.repo.WardEventRepo;
import com.ako.dbuff.dotapi.invoker.JSON;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInnerRunesLogInner;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  MatchDetailService.class,
  RuneEventMapper.class,
  WardEventMapper.class,
  UnitKillMapper.class
})
class OpenDotaLogMappersTest {

  private static final long MATCH_ID = 9100L;

  @Autowired private MatchDetailService service;
  @Autowired private RuneEventRepo runeRepo;
  @Autowired private WardEventRepo wardRepo;
  @Autowired private UnitKillRepo killRepo;

  private final MatchDomain match = MatchDomain.builder().id(MATCH_ID).build();

  @Test
  void mapsRunesInTheOrderOfTheLog() throws IOException {
    service.replace(fragment(), match);

    assertThat(runeRepo.findByMatchId(MATCH_ID))
        .filteredOn(rune -> rune.getPlayerSlot() == 2)
        .extracting(
            RuneEventDomain::getRuneIndex,
            RuneEventDomain::getEventTime,
            RuneEventDomain::getRuneType)
        .containsExactlyInAnyOrder(
            tuple(0, 142L, 7),
            tuple(1, 212L, 7),
            tuple(2, 253L, 7),
            tuple(3, 438L, 5),
            tuple(4, 543L, 5),
            tuple(5, 1072L, 0),
            tuple(6, 1424L, 2),
            tuple(7, 1699L, 1));
    assertThat(runeRepo.findByMatchId(MATCH_ID)).hasSize(10);
  }

  @Test
  void joinsEachRemovalToItsPlacementByHandle() throws IOException {
    service.replace(fragment(), match);

    assertThat(wardRepo.findByMatchId(MATCH_ID))
        .filteredOn(ward -> ward.getPlayerSlot() == 2)
        .extracting(
            WardEventDomain::getWardIndex,
            WardEventDomain::getWardKind,
            WardEventDomain::getPlacedTime,
            WardEventDomain::getRemovedTime,
            WardEventDomain::getRemovedBy,
            WardEventDomain::getRemovalKind)
        .containsExactlyInAnyOrder(
            tuple(
                0,
                WardKind.OBSERVER,
                -17L,
                343L,
                "npc_dota_hero_windrunner",
                WardRemovalKind.EXPIRED),
            tuple(1, WardKind.SENTRY, -13L, 174L, "npc_dota_hero_mirana", WardRemovalKind.KILLED),
            tuple(
                2,
                WardKind.SENTRY,
                314L,
                734L,
                "npc_dota_hero_windrunner",
                WardRemovalKind.EXPIRED),
            tuple(3, WardKind.OBSERVER, 970L, null, null, WardRemovalKind.UNKNOWN));
  }

  @Test
  void classifiesByTheLifeOfTheWard() throws IOException {
    service.replace(fragment(), match);

    assertThat(wardRepo.findByMatchId(MATCH_ID))
        .filteredOn(ward -> ward.getPlayerSlot() == 4)
        .extracting(WardEventDomain::getPlacedTime, WardEventDomain::getRemovalKind)
        .containsExactlyInAnyOrder(
            tuple(825L, WardRemovalKind.EXPIRED),
            tuple(880L, WardRemovalKind.EXPIRED),
            tuple(1093L, WardRemovalKind.KILLED),
            tuple(1343L, WardRemovalKind.EXPIRED));
  }

  @Test
  void keepsPlacementPositionAndHandle() throws IOException {
    service.replace(fragment(), match);

    assertThat(wardRepo.findByMatchId(MATCH_ID))
        .filteredOn(ward -> ward.getPlayerSlot() == 4 && ward.getPlacedTime() == 1093L)
        .singleElement()
        .satisfies(
            ward -> {
              assertThat(ward.getX()).isEqualTo(145.3);
              assertThat(ward.getY()).isEqualTo(89.2);
              assertThat(ward.getEhandle()).isEqualTo(5360545L);
            });
  }

  @Test
  void aReusedHandleLinksEachRemovalToTheLatestEarlierPlacement() {
    MatchResponsePlayersInner player = new MatchResponsePlayersInner().playerSlot(0L);
    player.setObsLog(List.of(placement(100, 7), placement(700, 7)));
    player.setObsLeftLog(
        List.of(removal(460, 7, "npc_dota_hero_a"), removal(900, 7, "npc_dota_hero_b")));
    MatchResponse response = new MatchResponse();
    response.setPlayers(List.of(player));

    service.replace(response, match);

    assertThat(wardRepo.findByMatchId(MATCH_ID))
        .extracting(
            WardEventDomain::getPlacedTime,
            WardEventDomain::getRemovedTime,
            WardEventDomain::getRemovalKind)
        .containsExactlyInAnyOrder(
            tuple(100L, 460L, WardRemovalKind.EXPIRED), tuple(700L, 900L, WardRemovalKind.KILLED));
  }

  @Test
  void mapsUnitKillCounts() throws IOException {
    service.replace(fragment(), match);

    assertThat(killRepo.findByMatchId(MATCH_ID))
        .filteredOn(kill -> kill.getPlayerSlot() == 4)
        .extracting(UnitKillDomain::getUnitName, UnitKillDomain::getKillCount)
        .containsExactlyInAnyOrder(
            tuple("npc_dota_neutral_polar_furbolg_ursa_warrior", 1L),
            tuple("npc_dota_creep_badguys_melee", 53L),
            tuple("npc_dota_creep_goodguys_melee", 3L),
            tuple("npc_dota_neutral_grown_frog_mage", 1L));
  }

  @Test
  void deleteByMatchIdRemovesOnlyThatMatch() throws IOException {
    service.replace(fragment(), match);
    service.replace(fragment(), MatchDomain.builder().id(MATCH_ID + 1).build());

    service.deleteByMatchId(MATCH_ID);

    assertThat(runeRepo.findAll()).hasSize(10).allMatch(row -> row.getMatchId() == MATCH_ID + 1);
    assertThat(wardRepo.findAll()).hasSize(8).allMatch(row -> row.getMatchId() == MATCH_ID + 1);
    assertThat(killRepo.findAll()).hasSize(8).allMatch(row -> row.getMatchId() == MATCH_ID + 1);
  }

  @Test
  void replaceDoesNotDuplicateRows() throws IOException {
    service.replace(fragment(), match);
    service.replace(fragment(), match);

    assertThat(runeRepo.count()).isEqualTo(10);
    assertThat(wardRepo.count()).isEqualTo(8);
    assertThat(killRepo.count()).isEqualTo(8);
  }

  @Test
  void aPayloadWithoutLogsWritesNothing() {
    MatchResponse bare = new MatchResponse();
    bare.setPlayers(List.of(new MatchResponsePlayersInner().playerSlot(0L)));

    service.replace(bare, match);

    assertThat(runeRepo.count() + wardRepo.count() + killRepo.count()).isZero();
  }

  @Test
  void anObserverAndASentryWithTheSameHandleAndSecondAreKeptApart() {
    MatchResponsePlayersInner player = new MatchResponsePlayersInner().playerSlot(0L);
    player.setObsLog(List.of(placement(100, 7)));
    player.setSenLog(List.of(placement(100, 7)));
    player.setSenLeftLog(List.of(removal(130, 7, "npc_dota_hero_a")));
    MatchResponse response = new MatchResponse();
    response.setPlayers(List.of(player));

    service.replace(response, match);

    assertThat(wardRepo.findByMatchId(MATCH_ID))
        .extracting(
            WardEventDomain::getWardIndex,
            WardEventDomain::getWardKind,
            WardEventDomain::getRemovedTime,
            WardEventDomain::getRemovalKind)
        .containsExactlyInAnyOrder(
            tuple(0, WardKind.OBSERVER, null, WardRemovalKind.UNKNOWN),
            tuple(1, WardKind.SENTRY, 130L, WardRemovalKind.KILLED));
  }

  @Test
  void entriesWithoutATimeOrANumericKeyOrCountAreSkipped() {
    MatchResponsePlayersInner player = new MatchResponsePlayersInner().playerSlot(0L);
    player.setRunesLog(List.of(rune(10L, "5"), rune(null, "5"), rune(20L, "x"), rune(30L, "8")));
    player.setKilled(Map.of("npc_dota_creep_a", 3, "npc_dota_creep_b", "many"));
    MatchResponse response = new MatchResponse();
    response.setPlayers(List.of(player));

    service.replace(response, match);

    assertThat(runeRepo.findByMatchId(MATCH_ID))
        .extracting(RuneEventDomain::getRuneIndex, RuneEventDomain::getRuneType)
        .containsExactlyInAnyOrder(tuple(0, 5), tuple(1, 8));
    assertThat(killRepo.findByMatchId(MATCH_ID))
        .extracting(UnitKillDomain::getUnitName)
        .containsExactly("npc_dota_creep_a");
  }

  private static MatchResponsePlayersInnerRunesLogInner rune(Long time, String key) {
    MatchResponsePlayersInnerRunesLogInner rune = new MatchResponsePlayersInnerRunesLogInner();
    rune.setTime(time);
    rune.setKey(key);
    return rune;
  }

  private static Map<String, Object> placement(long time, long handle) {
    return Map.of("time", time, "x", 100.0, "y", 100.0, "ehandle", handle);
  }

  private static Map<String, Object> removal(long time, long handle, String by) {
    return Map.of("time", time, "ehandle", handle, "attackername", by);
  }

  private static MatchResponse fragment() throws IOException {
    try (InputStream in =
        OpenDotaLogMappersTest.class.getResourceAsStream("/opendota/ward_rune_fragment.json")) {
      return new JSON().getMapper().readValue(in, MatchResponse.class);
    }
  }
}
