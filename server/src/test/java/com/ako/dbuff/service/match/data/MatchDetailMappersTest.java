package com.ako.dbuff.service.match.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ako.dbuff.dao.model.AbilityUpgradeDomain;
import com.ako.dbuff.dao.model.DraftEventDomain;
import com.ako.dbuff.dao.model.ItemPurchaseEventDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.TeamTimelineDomain;
import com.ako.dbuff.dao.repo.AbilityUpgradeRepo;
import com.ako.dbuff.dao.repo.DraftEventRepo;
import com.ako.dbuff.dao.repo.ItemPurchaseEventRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.TeamTimelineRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.dotapi.model.MatchResponsePicksBansInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInnerPurchaseLogInner;
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
  ItemPurchaseMapper.class
})
class MatchDetailMappersTest {

  private static final long MATCH_ID = 9000L;

  @Autowired private MatchDetailService service;
  @Autowired private DraftEventRepo draftRepo;
  @Autowired private AbilityUpgradeRepo upgradeRepo;
  @Autowired private PlayerTimelineRepo timelineRepo;
  @Autowired private TeamTimelineRepo teamTimelineRepo;
  @Autowired private ItemPurchaseEventRepo purchaseRepo;

  private final MatchDomain match = MatchDomain.builder().id(MATCH_ID).build();

  @Test
  void mapsEveryDetailSlice() {
    service.map(response(), match);

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
  void unparsedMatchWritesNothing() {
    MatchResponse bare = new MatchResponse();
    bare.setPlayers(List.of(new MatchResponsePlayersInner().playerSlot(0L)));

    service.map(bare, match);

    assertThat(draftRepo.count() + upgradeRepo.count() + timelineRepo.count()).isZero();
    assertThat(teamTimelineRepo.count() + purchaseRepo.count()).isZero();
  }

  @Test
  void deleteByMatchIdRemovesOnlyThatMatch() {
    service.map(response(), match);
    service.map(response(), MatchDomain.builder().id(MATCH_ID + 1).build());

    service.deleteByMatchId(MATCH_ID);

    assertThat(upgradeRepo.findAll()).allMatch(row -> row.getMatchId() == MATCH_ID + 1);
    assertThat(upgradeRepo.count()).isEqualTo(3);
    assertThat(draftRepo.count()).isEqualTo(2);
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
