package com.ako.dbuff.service.match.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ako.dbuff.dao.model.AbilityUpgradeDomain;
import com.ako.dbuff.dao.model.DraftEventDomain;
import com.ako.dbuff.dao.model.HeroTargetStatDomain;
import com.ako.dbuff.dao.model.ItemPurchaseEventDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.TargetStatKind;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.TeamTimelineDomain;
import com.ako.dbuff.dao.repo.AbilityUpgradeRepo;
import com.ako.dbuff.dao.repo.DeathEventRepo;
import com.ako.dbuff.dao.repo.HeroTargetStatRepo;
import com.ako.dbuff.dao.repo.ObjectiveEventRepo;
import com.ako.dbuff.dao.repo.TeamfightPlayerRepo;
import com.ako.dbuff.dao.repo.TeamfightRepo;
import com.ako.dbuff.dao.repo.DraftEventRepo;
import com.ako.dbuff.dao.repo.ItemPurchaseEventRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.TeamTimelineRepo;
import com.ako.dbuff.dotapi.invoker.JSON;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.dotapi.model.MatchResponsePicksBansInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInnerPurchaseLogInner;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  MatchDetailService.class,
  DraftEventMapper.class,
  AbilityUpgradeMapper.class,
  PlayerTimelineMapper.class,
  TeamTimelineMapper.class,
  ItemPurchaseMapper.class,
  DeathEventMapper.class,
  HeroTargetStatMapper.class,
  TeamfightMapper.class,
  ObjectiveEventMapper.class
})
class MatchDetailMappersTest {

  private static final long MATCH_ID = 9000L;

  @Autowired private MatchDetailService service;
  @Autowired private DraftEventRepo draftRepo;
  @Autowired private AbilityUpgradeRepo upgradeRepo;
  @Autowired private PlayerTimelineRepo timelineRepo;
  @Autowired private TeamTimelineRepo teamTimelineRepo;
  @Autowired private ItemPurchaseEventRepo purchaseRepo;
  @Autowired private DeathEventRepo deathRepo;
  @Autowired private HeroTargetStatRepo targetStatRepo;
  @Autowired private TeamfightRepo teamfightRepo;
  @Autowired private TeamfightPlayerRepo teamfightPlayerRepo;
  @Autowired private ObjectiveEventRepo objectiveRepo;

  private final MatchDomain match = MatchDomain.builder().id(MATCH_ID).build();

  @Test
  void mapsEveryDetailSlice() {
    service.replace(response(), match);

    assertThat(draftRepo.findAll())
        .extracting(DraftEventDomain::getDraftOrder, DraftEventDomain::getPick)
        .containsExactlyInAnyOrder(tuple(0, false), tuple(1, true));

    assertThat(upgradeRepo.findAll())
        .extracting(
            AbilityUpgradeDomain::getUpgradeIndex,
            AbilityUpgradeDomain::getAbilityId,
            AbilityUpgradeDomain::getPlayerId)
        .containsExactlyInAnyOrder(
            tuple(1, 5001L, -1L),
            tuple(2, 5002L, -1L),
            tuple(3, 5001L, -1L));

    assertThat(timelineRepo.findAll())
        .extracting(
            PlayerTimelineDomain::getGameMinute,
            PlayerTimelineDomain::getGold,
            PlayerTimelineDomain::getDenies)
        .containsExactlyInAnyOrder(
            tuple(0, 0L, null),
            tuple(1, 120L, null));

    assertThat(teamTimelineRepo.findAll())
        .extracting(
            TeamTimelineDomain::getGameMinute,
            TeamTimelineDomain::getRadiantGoldAdv,
            TeamTimelineDomain::getRadiantXpAdv)
        .containsExactlyInAnyOrder(
            tuple(0, 0L, 0L),
            tuple(1, -350L, null));

    assertThat(purchaseRepo.findAll())
        .extracting(
            ItemPurchaseEventDomain::getPurchaseIndex,
            ItemPurchaseEventDomain::getItemKey,
            ItemPurchaseEventDomain::getPurchaseTime)
        .containsExactlyInAnyOrder(
            tuple(0, "tango", -80L),
            tuple(1, "tango", -80L));
  }

  @Test
  void mapsRealParsedPayload() throws IOException {
    service.replace(realPayload(), match);

    assertThat(timelineRepo.findAll())
        .hasSize(15)
        .allMatch(row -> row.getNetworth() != null && row.getHeroDamage() != null);
    assertThat(upgradeRepo.findAll()).extracting(AbilityUpgradeDomain::getPlayerId).contains(-1L);
    assertThat(deathRepo.findAll())
        .isNotEmpty()
        .allMatch(death -> death.getKillerHeroName() != null && death.getTimeDead() != null);
    assertThat(targetStatRepo.findAll())
        .extracting(HeroTargetStatDomain::getStatKind)
        .contains(TargetStatKind.DAMAGE, TargetStatKind.ABILITY_USE);
    assertThat(targetStatRepo.findAll())
        .allMatch(stat -> stat.getTargetHeroName().startsWith("npc_dota_hero_"));
    assertThat(teamfightRepo.count()).isEqualTo(2);
    assertThat(teamfightPlayerRepo.count()).isEqualTo(6);
    assertThat(objectiveRepo.findAll())
        .hasSize(5)
        .anyMatch(event -> "building_kill".equals(event.getEventType()));
  }

  @Test
  void parsedMatchIsStampedAsMapped() throws IOException {
    service.replace(realPayload(), match);

    assertThat(match.getHasParsed()).isTrue();
    assertThat(match.getReplayCluster()).isNotNull();
    assertThat(match.getDetailsMappedAt()).isNotNull();
    assertThat(match.getDetailsAttemptedAt()).isNotNull();
  }

  @Test
  void unparsedMatchIsOnlyMarkedAsAttempted() {
    service.replace(response(), match);

    assertThat(match.getHasParsed()).isFalse();
    assertThat(match.getDetailsMappedAt()).isNull();
    assertThat(match.getDetailsAttemptedAt()).isNotNull();
  }

  @Test
  void teamfightPlayersAreSkippedWhenTheyDoNotLineUpWithTheMatchPlayers() throws IOException {
    MatchResponse payload = realPayload();
    payload.setPlayers(payload.getPlayers().subList(0, 2));

    service.replace(payload, match);

    assertThat(teamfightRepo.count()).isEqualTo(2);
    assertThat(teamfightPlayerRepo.count()).isZero();
  }

  @Test
  void unparsedMatchWritesNothing() {
    MatchResponse bare = new MatchResponse();
    bare.setPlayers(List.of(new MatchResponsePlayersInner().playerSlot(0L)));

    service.replace(bare, match);

    assertThat(draftRepo.count() + upgradeRepo.count() + timelineRepo.count()).isZero();
    assertThat(teamTimelineRepo.count() + purchaseRepo.count()).isZero();
    assertThat(deathRepo.count() + targetStatRepo.count() + teamfightRepo.count()).isZero();
    assertThat(teamfightPlayerRepo.count() + objectiveRepo.count()).isZero();
  }

  @Test
  void deleteByMatchIdRemovesOnlyThatMatch() {
    service.replace(response(), match);
    service.replace(response(), MatchDomain.builder().id(MATCH_ID + 1).build());

    service.deleteByMatchId(MATCH_ID);

    assertThat(upgradeRepo.findAll()).allMatch(row -> row.getMatchId() == MATCH_ID + 1);
    assertThat(upgradeRepo.count()).isEqualTo(3);
    assertThat(draftRepo.count()).isEqualTo(2);
  }

  private static MatchResponse realPayload() throws IOException {
    try (InputStream in =
        MatchDetailMappersTest.class.getResourceAsStream("/opendota/ad_match_trimmed.json")) {
      return new JSON().getMapper().readValue(in, MatchResponse.class);
    }
  }

  private static MatchResponse response() {
    MatchResponse response = new MatchResponse();
    response.setPicksBans(List.of(pickBan(0L, false), pickBan(1L, true)));
    response.setRadiantGoldAdv(List.of(BigDecimal.ZERO, new BigDecimal("-350")));
    response.setRadiantXpAdv(List.of(BigDecimal.ZERO));
    MatchResponsePlayersInner player = new MatchResponsePlayersInner().playerSlot(0L);
    player.setAbilityUpgradesArr(List.of(5001L, 5002L, 5001L));
    player.setGoldT(List.of(0L, 120L));
    player.setPurchaseLog(List.of(purchase("tango", -80L), purchase("tango", -80L)));
    response.setPlayers(List.of(player));
    return response;
  }

  private static MatchResponsePicksBansInner pickBan(long order, boolean pick) {
    MatchResponsePicksBansInner pickBan = new MatchResponsePicksBansInner();
    pickBan.setOrder(order);
    pickBan.setIsPick(pick);
    pickBan.setHeroId(1L);
    pickBan.setTeam(0L);
    return pickBan;
  }

  private static MatchResponsePlayersInnerPurchaseLogInner purchase(String key, long time) {
    MatchResponsePlayersInnerPurchaseLogInner purchase =
        new MatchResponsePlayersInnerPurchaseLogInner();
    purchase.setKey(key);
    purchase.setTime(time);
    return purchase;
  }
}
