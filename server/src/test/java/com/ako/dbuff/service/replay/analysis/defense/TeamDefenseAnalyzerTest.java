package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.dao.model.PlayerDefenseTrackDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import com.ako.dbuff.dao.repo.PlayerDefenseTrackRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.TeamDefensePointRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.DamageType;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.item.ItemKits;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimelines;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class TeamDefenseAnalyzerTest {
  private static final long MATCH = 7L;
  private static final int MINUTES = 12;
  private static final long RADIANT_SLOT = 0;
  private static final long DIRE_SLOT = 128;
  private static final int FLAGS = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;

  private final ReplayPlayerTrackRepo trackRepo = Mockito.mock(ReplayPlayerTrackRepo.class);
  private final ReplayPlayerRepo playerRepo = Mockito.mock(ReplayPlayerRepo.class);
  private final PlayerTimelineRepo timelineRepo = Mockito.mock(PlayerTimelineRepo.class);
  private final CombatLogReader combatLog = Mockito.mock(CombatLogReader.class);
  private final HeroStateTimelines stateTimelines = Mockito.mock(HeroStateTimelines.class);
  private final ConstantsManagers constants = Mockito.mock(ConstantsManagers.class);
  private final PlayerDefenseTrackRepo trackOut = Mockito.mock(PlayerDefenseTrackRepo.class);
  private final TeamDefensePointRepo pointOut = Mockito.mock(TeamDefensePointRepo.class);

  private final List<PlayerDefenseTrackDomain> savedTracks = new ArrayList<>();
  private final List<TeamDefensePointDomain> savedPoints = new ArrayList<>();
  private final List<ReplayPlayerDomain> roster = new ArrayList<>();
  private final List<CombatEvent> events = new ArrayList<>();
  private final Map<Integer, HeroStateTimeline> timelines = new HashMap<>();

  private static Map<String, String> attr(String value, String display) {
    return Map.of("key", "k", "value", value, "display", display);
  }

  private final Map<String, ItemConstant> items =
      Map.of(
          "platemail",
              ItemConstant.builder().attrib(List.of(attr("10", "+ {value} Armor"))).build(),
          "cloak",
              ItemConstant.builder()
                  .attrib(List.of(attr("18", "+ {value}% Magic Resistance")))
                  .build());
  private final ItemKits kits = key -> Optional.ofNullable(items.get(key));

  private TeamDefenseAnalyzer analyzer;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    Mockito.when(playerRepo.findByMatchId(MATCH)).thenAnswer(i -> roster);
    Mockito.when(timelineRepo.findByMatchId(MATCH)).thenReturn(List.<PlayerTimelineDomain>of());
    Mockito.when(combatLog.events(MATCH)).thenAnswer(i -> events);
    Mockito.when(stateTimelines.load(MATCH)).thenAnswer(i -> timelines);
    Mockito.when(constants.getAllAbilityConstants()).thenReturn(Map.of());
    Mockito.when(trackOut.saveAll(any()))
        .thenAnswer(
            i -> {
              ((Iterable<PlayerDefenseTrackDomain>) i.getArgument(0)).forEach(savedTracks::add);
              return List.of();
            });
    Mockito.when(pointOut.saveAll(any()))
        .thenAnswer(
            i -> {
              ((Iterable<TeamDefensePointDomain>) i.getArgument(0)).forEach(savedPoints::add);
              return List.of();
            });
    analyzer =
        new TeamDefenseAnalyzer(
            trackRepo,
            playerRepo,
            timelineRepo,
            combatLog,
            stateTimelines,
            new DefenseItemBonuses(kits),
            new DefenseProperties(),
            constants,
            trackOut,
            pointOut);
    roster.add(player(RADIANT_SLOT, "Axe"));
    roster.add(player(DIRE_SLOT, "Pudge"));
  }

  private static ReplayPlayerDomain player(long slot, String entity) {
    return ReplayPlayerDomain.builder()
        .matchId(MATCH)
        .playerSlot(slot)
        .replayPlayerId((int) slot)
        .heroEntityName(entity)
        .build();
  }

  private static ReplayPlayerTrackDomain track(long slot, boolean withBase) {
    int[] constant = new int[MINUTES];
    int[] hp = new int[MINUTES];
    Arrays.fill(hp, 1000);
    Arrays.fill(constant, 1);
    int[] percent = new int[MINUTES];
    Arrays.fill(percent, 100);
    ReplayPlayerTrackDomain.ReplayPlayerTrackDomainBuilder b =
        ReplayPlayerTrackDomain.builder()
            .matchId(MATCH)
            .playerSlot(slot)
            .startSeconds(0)
            .stepSeconds(60)
            .sampleCount(MINUTES)
            .maxHp(hp)
            .hpPercent(percent)
            .flags(constant);
    if (withBase) {
      b.baseArmorX10(filled(10)).baseMagicResistX10(filled(250)).agilityTotalX10(filled(600));
    }
    return b.build();
  }

  private static int[] filled(int value) {
    int[] a = new int[MINUTES];
    Arrays.fill(a, value);
    return a;
  }

  private static CombatEvent physicalHit(double second, String attacker, String target) {
    return new CombatEvent(
        CombatEventKind.DAMAGE,
        second,
        attacker,
        target,
        "",
        FLAGS,
        100,
        500,
        0,
        0,
        0,
        0,
        0,
        DamageType.PHYSICAL);
  }

  private static HeroStateTimeline timeline(double second, String item) {
    HeroStateTimeline t = new HeroStateTimeline();
    t.add(new HeroStateTimeline.Change(second, StateChangeKind.ITEM_ADDED, item, 0));
    return t;
  }

  @Test
  void armorAndMagicResistFollowAgilityAndHeldItems() {
    Mockito.when(trackRepo.findByMatchId(MATCH))
        .thenReturn(List.of(track(RADIANT_SLOT, true), track(DIRE_SLOT, true)));
    timelines.put(0, timeline(600, "item_platemail"));
    timelines.put(5, timeline(0, "item_cloak"));
    events.add(physicalHit(30, "npc_dota_hero_pudge", "npc_dota_hero_axe"));
    events.add(physicalHit(40, "npc_dota_hero_axe", "npc_dota_hero_pudge"));

    analyzer.analyze(MATCH);

    PlayerDefenseTrackDomain radiant = trackOf(RADIANT_SLOT);
    assertThat(radiant.getArmorX10()[9]).isEqualTo(110);
    assertThat(radiant.getArmorX10()[10]).isEqualTo(210);
    assertThat(radiant.getMagicResistX10()[10]).isEqualTo(250);
    assertThat(trackOf(DIRE_SLOT).getMagicResistX10()[3]).isEqualTo(385);
    assertThat(radiant.getStartMinute()).isZero();
    assertThat(radiant.getEhpMix()[0]).isEqualTo(PlayerDefenseTrackDomain.MISSING);
    assertThat(radiant.getEhpMix()[1]).isPositive();
  }

  @Test
  void oneRowPerMinuteAndTeamPlusEndRowAndCheckpointsOnlyWhenReached() {
    Mockito.when(trackRepo.findByMatchId(MATCH))
        .thenReturn(List.of(track(RADIANT_SLOT, true), track(DIRE_SLOT, true)));

    analyzer.analyze(MATCH);

    List<TeamDefensePointDomain> radiant =
        savedPoints.stream().filter(p -> p.getTeam().equals("RADIANT")).toList();
    assertThat(radiant).hasSize(MINUTES + 1);
    assertThat(radiant.get(MINUTES).getCheckpoint()).isEqualTo("END");
    assertThat(radiant.get(MINUTES).getMinute()).isEqualTo(MINUTES - 1);
    assertThat(radiant.stream().map(TeamDefensePointDomain::getCheckpoint).filter(c -> c != null))
        .containsExactly("M10", "END");
    assertThat(radiant.get(10).getMaxHpSum()).isEqualTo(1000);
    assertThat(radiant.get(10).getNetWorth()).isNull();
    assertThat(radiant.get(10).getAlive()).isEqualTo(1);
    assertThat(trackRepoSaved()).isEqualTo(2);
  }

  @Test
  void trackWithoutBaseValuesGivesHealthButNoArmorResistanceOrEhp() {
    Mockito.when(trackRepo.findByMatchId(MATCH)).thenReturn(List.of(track(RADIANT_SLOT, false)));

    analyzer.analyze(MATCH);

    PlayerDefenseTrackDomain row = trackOf(RADIANT_SLOT);
    assertThat(row.getMaxHp()).hasSize(MINUTES).containsOnly(1000);
    assertThat(row.getArmorX10()).isNull();
    assertThat(row.getMagicResistX10()).isNull();
    assertThat(row.getEhpPhys()).isNull();
    assertThat(row.getEhpMix()).isNull();
    TeamDefensePointDomain point = savedPoints.get(5);
    assertThat(point.getMaxHpSum()).isEqualTo(1000);
    assertThat(point.getArmorAvgX10()).isNull();
    assertThat(point.getEhpPhys()).isNull();
  }

  @Test
  void noKnownDamageSkipsTheMixedEhp() {
    Mockito.when(trackRepo.findByMatchId(MATCH))
        .thenReturn(List.of(track(RADIANT_SLOT, true), track(DIRE_SLOT, true)));

    analyzer.analyze(MATCH);

    TeamDefensePointDomain point = savedPoints.get(5);
    assertThat(point.getEhpPhys()).isPositive();
    assertThat(point.getEhpMix()).isNull();
    assertThat(point.getEnemyPhysShare()).isNull();
  }

  @Test
  void zeroSampleOfMissingHeroCarriesForwardLastValidValuesAndStaysDead() {
    ReplayPlayerTrackDomain dire = track(DIRE_SLOT, true);
    int last = MINUTES - 1;
    dire.getMaxHp()[last] = 0;
    dire.getBaseArmorX10()[last] = 0;
    dire.getBaseMagicResistX10()[last] = 0;
    dire.getAgilityTotalX10()[last] = 0;
    dire.getFlags()[last] = 0;
    dire.getHpPercent()[last] = 0;
    Mockito.when(trackRepo.findByMatchId(MATCH))
        .thenReturn(List.of(track(RADIANT_SLOT, true), dire));

    analyzer.analyze(MATCH);

    PlayerDefenseTrackDomain row = trackOf(DIRE_SLOT);
    assertThat(row.getMaxHp()[last]).isEqualTo(1000);
    assertThat(row.getArmorX10()[last]).isEqualTo(row.getArmorX10()[last - 1]);
    assertThat(row.getMagicResistX10()[last]).isEqualTo(250);
    TeamDefensePointDomain end =
        savedPoints.stream()
            .filter(p -> p.getTeam().equals("DIRE") && "END".equals(p.getCheckpoint()))
            .findFirst()
            .orElseThrow();
    assertThat(end.getMaxHpSum()).isEqualTo(1000);
    assertThat(end.getEhpPhys()).isPositive();
    assertThat(end.getAlive()).isZero();
    TeamDefensePointDomain before =
        savedPoints.stream()
            .filter(p -> p.getTeam().equals("DIRE") && p.getMinute() == last - 1)
            .findFirst()
            .orElseThrow();
    assertThat(before.getMaxHpSum()).isEqualTo(1000);
    assertThat(before.getAlive()).isEqualTo(1);
  }

  @Test
  void zeroSampleWithoutEarlierValidSampleKeepsNullEhp() {
    ReplayPlayerTrackDomain dire = track(DIRE_SLOT, true);
    Arrays.fill(dire.getMaxHp(), 0);
    Mockito.when(trackRepo.findByMatchId(MATCH)).thenReturn(List.of(dire));

    analyzer.analyze(MATCH);

    TeamDefensePointDomain point =
        savedPoints.stream().filter(p -> p.getTeam().equals("DIRE")).findFirst().orElseThrow();
    assertThat(point.getEhpPhys()).isNull();
  }

  private int trackRepoSaved() {
    return savedTracks.size();
  }

  private PlayerDefenseTrackDomain trackOf(long slot) {
    return savedTracks.stream().filter(t -> t.getPlayerSlot() == slot).findFirst().orElseThrow();
  }
}
