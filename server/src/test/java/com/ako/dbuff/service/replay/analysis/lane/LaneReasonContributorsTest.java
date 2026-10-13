package com.ako.dbuff.service.replay.analysis.lane;

import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.map.Zone;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

class LaneReasonContributorsTest {
  private static final long AXE = 0;
  private static final long LICH = 1;
  private static final long ROAMER = 2;
  private static final long MIRANA = 128;
  private static final long DOOM = 129;
  private static final long OUTSIDER = 130;

  private final Map<Long, LaneHero> laneHeroes = new HashMap<>();
  private final Map<Long, HeroLaneStats> stats = new HashMap<>();
  private final Map<Long, Integer> roles = new HashMap<>();
  private List<LaneDeath> deaths = List.of();
  private List<RunePickupDomain> runes = List.of();

  private LaneHero hero(long slot, Zone.Lane lane, int start, int end) {
    LaneHero hero =
        new LaneHero(
            slot, TeamSide.ofSlot(slot), lane, new LaningWindow(start, end, LaneEnd.JUNGLE));
    laneHeroes.put(slot, hero);
    return hero;
  }

  private static HeroLaneStats stats(long slot, int siege, int denies, int stacks) {
    return new HeroLaneStats(
        slot, 10, 6, 3, siege, 0, denies, 900, 1000.0, 1000.0, 5, 0, 0, 0, 0, 0, stacks);
  }

  private LaneContext context(
      Zone.Lane lane, TeamSide side, DamageLedger ledger, Set<Long> withoutLane) {
    laneHeroes.values().forEach(h -> stats.putIfAbsent(h.slot(), stats(h.slot(), 0, 0, 0)));
    Map<Long, String> names =
        Map.of(
            AXE, "Axe", LICH, "Lich", ROAMER, "Rubick", MIRANA, "Mirana", DOOM, "Doom", OUTSIDER,
            "Tusk");
    LaneFacts facts =
        new LaneFacts(
            new LaneProperties(),
            names,
            roles,
            Map.of(),
            laneHeroes,
            withoutLane,
            stats,
            ledger,
            deaths,
            runes);
    LanePair pair =
        new LanePair(
            lane,
            new LaningWindow(0, 480, LaneEnd.JUNGLE),
            laneHeroes.values().stream().filter(h -> h.lane() == lane).toList());
    return new LaneContext(
        facts,
        pair,
        side,
        SideTotals.of(
            side, pair.side(side).stream().map(h -> stats.get(h.slot())).toList(), false, false),
        SideTotals.of(
            LanePair.other(side),
            pair.side(LanePair.other(side)).stream().map(h -> stats.get(h.slot())).toList(),
            false,
            false));
  }

  private LaneContext context(Zone.Lane lane, TeamSide side) {
    return context(lane, side, new DamageLedger(List.of(), Map.of(), Map.of()), Set.of());
  }

  @Test
  void partnerAbsent_namesAPartnerWhoLeftEarly() {
    hero(AXE, Zone.Lane.BOTTOM, 10, 480);
    hero(LICH, Zone.Lane.BOTTOM, 10, 240);
    hero(MIRANA, Zone.Lane.BOTTOM, 10, 480);

    List<LaneReason> reasons =
        new PartnerAbsentContributor().contribute(context(Zone.Lane.BOTTOM, TeamSide.RADIANT));

    assertThat(reasons)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.code()).isEqualTo("PARTNER_ABSENT");
              assertThat(r.subjectSlot()).isEqualTo(LICH);
              assertThat(r.direction()).isEqualTo(LaneReason.Direction.HURT);
              assertThat(r.severity()).isBetween(0.4, 0.6);
              assertThat(r.evidence()).contains("Lich").contains("4:00");
            });
  }

  @Test
  void partnerAbsent_findsARoamingSupportWhenAHeroLanesAlone() {
    hero(AXE, Zone.Lane.BOTTOM, 10, 480);
    hero(MIRANA, Zone.Lane.BOTTOM, 10, 480);
    roles.put(ROAMER, 4);

    List<LaneReason> reasons =
        new PartnerAbsentContributor()
            .contribute(
                context(
                    Zone.Lane.BOTTOM,
                    TeamSide.RADIANT,
                    new DamageLedger(List.of(), Map.of(), Map.of()),
                    Set.of(ROAMER)));

    assertThat(reasons)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.code()).isEqualTo("PARTNER_ROAMING");
              assertThat(r.subjectSlot()).isEqualTo(ROAMER);
            });
  }

  @Test
  void partnerAbsent_saysNothingWhenBothPartnersStayed() {
    hero(AXE, Zone.Lane.BOTTOM, 10, 480);
    hero(LICH, Zone.Lane.BOTTOM, 30, 470);

    assertThat(
            new PartnerAbsentContributor().contribute(context(Zone.Lane.BOTTOM, TeamSide.RADIANT)))
        .isEmpty();
  }

  @Test
  void creepEdge_namesASiegeAndADenyLeadFromBothSides() {
    hero(AXE, Zone.Lane.TOP, 10, 480);
    hero(MIRANA, Zone.Lane.TOP, 10, 480);
    stats.put(AXE, stats(AXE, 3, 8, 0));
    stats.put(MIRANA, stats(MIRANA, 1, 2, 0));

    List<LaneReason> axe =
        new CreepEdgeContributor().contribute(context(Zone.Lane.TOP, TeamSide.RADIANT));
    List<LaneReason> mirana =
        new CreepEdgeContributor().contribute(context(Zone.Lane.TOP, TeamSide.DIRE));

    assertThat(axe)
        .extracting(LaneReason::code, LaneReason::direction)
        .containsExactlyInAnyOrder(
            tuple("SIEGE_EDGE", LaneReason.Direction.HELPED),
            tuple("DENY_EDGE", LaneReason.Direction.HELPED));
    assertThat(mirana).extracting(LaneReason::direction).containsOnly(LaneReason.Direction.HURT);
  }

  @Test
  void supportPresence_isAReasonOnlyWhenOneSideHasASupport() {
    hero(AXE, Zone.Lane.TOP, 10, 480);
    hero(LICH, Zone.Lane.TOP, 10, 480);
    hero(MIRANA, Zone.Lane.TOP, 10, 480);
    roles.put(LICH, 5);

    assertThat(
            new SupportPresenceContributor().contribute(context(Zone.Lane.TOP, TeamSide.RADIANT)))
        .singleElement()
        .satisfies(r -> assertThat(r.direction()).isEqualTo(LaneReason.Direction.HELPED));
    assertThat(new SupportPresenceContributor().contribute(context(Zone.Lane.TOP, TeamSide.DIRE)))
        .singleElement()
        .satisfies(r -> assertThat(r.direction()).isEqualTo(LaneReason.Direction.HURT));
    roles.put(DOOM, 4);
    hero(DOOM, Zone.Lane.TOP, 10, 480);
    assertThat(new SupportPresenceContributor().contribute(context(Zone.Lane.TOP, TeamSide.DIRE)))
        .isEmpty();
  }

  @Test
  void earlyKill_listsAnEarlyDeathAndTheKillForTheOtherSide() {
    hero(AXE, Zone.Lane.TOP, 10, 480);
    hero(MIRANA, Zone.Lane.TOP, 10, 480);
    deaths = List.of(new LaneDeath(AXE, MIRANA, 150), new LaneDeath(MIRANA, AXE, 400));

    List<LaneReason> radiant =
        new EarlyKillContributor().contribute(context(Zone.Lane.TOP, TeamSide.RADIANT));
    List<LaneReason> dire =
        new EarlyKillContributor().contribute(context(Zone.Lane.TOP, TeamSide.DIRE));

    assertThat(radiant)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.code()).isEqualTo("EARLY_DEATH");
              assertThat(r.subjectSlot()).isEqualTo(AXE);
            });
    assertThat(dire)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.code()).isEqualTo("EARLY_KILL");
              assertThat(r.subjectSlot()).isEqualTo(MIRANA);
            });
  }

  @Test
  void enemyGank_countsKillsAndDamageOfHeroesFromOtherLanesAndOfHeroesWithNoLane() {
    hero(AXE, Zone.Lane.TOP, 10, 480);
    hero(MIRANA, Zone.Lane.TOP, 10, 480);
    hero(DOOM, Zone.Lane.BOTTOM, 10, 480);
    deaths = List.of(new LaneDeath(AXE, OUTSIDER, 200), new LaneDeath(AXE, DOOM, 300));
    DamageLedger ledger =
        new DamageLedger(
            List.of(
                damage("npc_dota_hero_tusk", "npc_dota_hero_axe", 700, 100),
                damage("npc_dota_hero_doom_bringer", "npc_dota_hero_axe", 100, 120)),
            Map.of(
                "npc_dota_hero_tusk", OUTSIDER,
                "npc_dota_hero_doom_bringer", DOOM,
                "npc_dota_hero_axe", AXE),
            Map.of(AXE, new LaningWindow(10, 480, LaneEnd.JUNGLE)));

    List<LaneReason> reasons =
        new EnemyGankContributor()
            .contribute(context(Zone.Lane.TOP, TeamSide.RADIANT, ledger, Set.of(OUTSIDER)));

    assertThat(reasons)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.subjectSlot()).as("most kills, then most damage").isEqualTo(OUTSIDER);
              assertThat(r.evidence())
                  .contains("Doom and Tusk")
                  .contains("killed 2")
                  .contains("800");
              assertThat(r.direction()).isEqualTo(LaneReason.Direction.HURT);
            });
  }

  private static CombatEvent damage(String attacker, String target, int value, double second) {
    return new CombatEvent(
        CombatEventKind.DAMAGE,
        second,
        attacker,
        target,
        "",
        CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
        value,
        0,
        0,
        0,
        0,
        0,
        0);
  }

  @Test
  void harassment_countsOnlyTheDamageOfTheLaneOpponentsNotOfOtherLanes() {
    hero(AXE, Zone.Lane.TOP, 10, 480);
    hero(MIRANA, Zone.Lane.TOP, 10, 480);
    hero(DOOM, Zone.Lane.BOTTOM, 10, 480);
    Map<String, Long> slots =
        Map.of(
            "npc_dota_hero_axe", AXE, "npc_dota_hero_mirana", MIRANA, "npc_dota_hero_doom", DOOM);
    Map<Long, LaningWindow> windows = new HashMap<>();
    laneHeroes.forEach((slot, h) -> windows.put(slot, h.window()));
    DamageLedger ledger =
        new DamageLedger(
            List.of(
                damage("npc_dota_hero_mirana", "npc_dota_hero_axe", 200, 100),
                damage("npc_dota_hero_axe", "npc_dota_hero_mirana", 200, 110),
                damage("npc_dota_hero_doom", "npc_dota_hero_axe", 900, 120)),
            slots,
            windows);

    assertThat(
            new HarassmentContributor()
                .contribute(context(Zone.Lane.TOP, TeamSide.RADIANT, ledger, Set.of())))
        .as("200 taken against 200 dealt; Doom's 900 is a gank")
        .isEmpty();
  }

  @Test
  void earlyKill_keepsOneRowPerHeroAndCountsTheDeathsInTheEvidence() {
    hero(AXE, Zone.Lane.TOP, 10, 480);
    hero(MIRANA, Zone.Lane.TOP, 10, 480);
    deaths = List.of(new LaneDeath(AXE, MIRANA, 150), new LaneDeath(AXE, MIRANA, 250));

    List<LaneReason> radiant =
        new EarlyKillContributor().contribute(context(Zone.Lane.TOP, TeamSide.RADIANT));
    List<LaneReason> dire =
        new EarlyKillContributor().contribute(context(Zone.Lane.TOP, TeamSide.DIRE));

    assertThat(radiant)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.evidence()).contains("2 times").contains("2:30").contains("4:10");
              assertThat(r.severity()).as("the first death sets it").isCloseTo(0.45, within(0.01));
            });
    assertThat(dire)
        .singleElement()
        .satisfies(r -> assertThat(r.evidence()).contains("Axe at 2:30").contains("Axe at 4:10"));
  }
}
