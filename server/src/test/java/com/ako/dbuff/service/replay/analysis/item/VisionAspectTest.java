package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.model.WardKind;
import com.ako.dbuff.dao.model.WardRemovalKind;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.vision.DetectionLog;
import com.ako.dbuff.service.replay.analysis.vision.InvisibilityLog;
import com.ako.dbuff.service.replay.analysis.vision.InvisibleHeroes;
import com.ako.dbuff.service.replay.analysis.vision.VisionProperties;
import com.ako.dbuff.service.replay.report.VisionKindWording;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.change;
import static org.assertj.core.api.Assertions.assertThat;

class VisionAspectTest {
  private static final String ME = ItemTestData.HERO;
  private static final String ENEMY = "npc_dota_hero_riki";
  private static final int HEROES = CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO;
  private static final Map<String, Long> SLOTS = Map.of(ME, 0L, ENEMY, 128L);
  private static final InvisibleHeroes NO_SOURCES =
      new InvisibleHeroes() {
        @Override
        public Map<String, Source> abilities() {
          return Map.of();
        }

        @Override
        public Set<String> items() {
          return Set.of();
        }
      };
  private static final ItemKits KITS =
      ItemTestData.kits(Map.of("ward_observer", 0L, "ward_sentry", 50L, "dust", 80L, "gem", 900L));
  private static final VisionAspect ASPECT = new VisionAspect(new VisionProperties());

  private static ItemEfficiencyEntry entry(String key) {
    return ItemTestData.entry(key, List.of(VisionAspect.NAME), List.of(key), Map.of());
  }

  private static ReplayPlayerTrackDomain trackAt(int x, int y) {
    int[] xs = new int[200];
    int[] ys = new int[200];
    Arrays.fill(xs, x);
    Arrays.fill(ys, y);
    return ReplayPlayerTrackDomain.builder()
        .startSeconds(ItemTestData.START)
        .stepSeconds(ItemTestData.STEP)
        .sampleCount(200)
        .x(xs)
        .y(ys)
        .build();
  }

  private static CombatEvent modifier(CombatEventKind kind, double second) {
    return new CombatEvent(
        kind, second, ME, ENEMY, "modifier_invisible", HEROES, 0, 0, 0, 0, 0, 0, 0);
  }

  private static CombatEvent died(double second) {
    return new CombatEvent(
        CombatEventKind.DEATH, second, ME, ENEMY, "", HEROES, 0, 0, 0, 0, 0, 0, 0);
  }

  private static CombatEvent bounty(double second, int gold) {
    return new CombatEvent(CombatEventKind.GOLD, second, "", ME, "", 0, gold, 0, 12, 0, 0, 0, 0);
  }

  private static WardEventDomain ward(
      long slot, WardKind kind, long placed, Long removed, WardRemovalKind how, String by) {
    return WardEventDomain.builder()
        .playerSlot(slot)
        .wardKind(kind)
        .placedTime(placed)
        .x(128.0)
        .y(128.0)
        .removedTime(removed)
        .removalKind(how)
        .removedBy(by)
        .build();
  }

  private static List<ReplayStateChangeDomain> bought(String item, int times) {
    List<ReplayStateChangeDomain> changes = new ArrayList<>();
    for (int i = 0; i < times; i++) {
      changes.add(change(10 + i * 20, StateChangeKind.ITEM_ADDED, item));
    }
    return changes;
  }

  private static CombatEvent dustUse(double second) {
    return new CombatEvent(
        CombatEventKind.ITEM, second, ME, "", "item_dust", HEROES, 0, 0, 0, 0, 0, 0, 0);
  }

  private static ItemEfficiency judge(
      String key,
      List<ReplayStateChangeDomain> changes,
      List<WardEventDomain> wards,
      List<CombatEvent> events,
      ReplayPlayerTrackDomain mine,
      ReplayPlayerTrackDomain victim,
      InvisibleHeroes sources) {
    List<CombatEvent> all = new ArrayList<>(events);
    VisionFacts vision =
        new VisionFacts(
            wards,
            new DetectionLog(all, SLOTS, changes.stream().map(c -> withSlot(c, 0)).toList()),
            new InvisibilityLog(all, SLOTS, sources),
            Map.of(),
            Map.of(0L, mine, 128L, victim));
    MatchFacts match = new MatchFacts(all, SLOTS, Map.of(), vision);
    HeroInputs hero =
        ItemTestData.heroWith(
            ItemTestData.hero(changes, events, mine, List.of(), List.of()), match);
    return ItemTestData.judge(entry(key), List.of(ASPECT), KITS, 900, List.of(hero)).getFirst();
  }

  private static ReplayStateChangeDomain withSlot(ReplayStateChangeDomain c, long slot) {
    c.setPlayerSlot(slot);
    return c;
  }

  private static final List<CombatEvent> INVISIBLE_KILL =
      List.of(
          modifier(CombatEventKind.MODIFIER_ADD, 95),
          modifier(CombatEventKind.MODIFIER_REMOVE, 110),
          died(100),
          bounty(100, 400));

  @Test
  void aSentryOverAnInvisibleEnemyWhoDiedEarnsAShareOfTheKill() {
    ItemEfficiency row =
        judge(
            "ward_sentry",
            bought("item_ward_sentry", 8),
            List.of(ward(0, WardKind.SENTRY, 50, null, WardRemovalKind.UNKNOWN, null)),
            INVISIBLE_KILL,
            trackAt(2000, 0),
            trackAt(0, 0),
            NO_SOURCES);

    assertThat(row.figures())
        .containsEntry("visionBought", 8.0)
        .containsEntry("visionPlaced", 1.0)
        .containsEntry("visionInvisibleKills", 1.0)
        .containsEntry("visionKillShare", 100.0)
        .containsEntry("gain.vision", 100.0);
    assertThat(row.rows()).extracting(d -> d.label()).containsExactly("INVISIBLE_KILL");
    assertThat(row.cost()).isEqualTo(400);
    assertThat(row.ratio()).isEqualTo(100.0 / 400);
    assertThat(VisionKindWording.line(row)).startsWith("1 sentry, 1 Riki kill");
  }

  @Test
  void anEnemyWhoWasNotInvisibleIsNoKillOfTheSentry() {
    ItemEfficiency row =
        judge(
            "ward_sentry",
            bought("item_ward_sentry", 8),
            List.of(ward(0, WardKind.SENTRY, 50, null, WardRemovalKind.UNKNOWN, null)),
            List.of(died(100), bounty(100, 400)),
            trackAt(2000, 0),
            trackAt(0, 0),
            NO_SOURCES);

    assertThat(row.figures()).containsEntry("visionInvisibleKills", 0.0);
    assertThat(row.gain()).isZero();
  }

  @Test
  void aSentryOutOfRangeOrGoneIsNoKillOfTheSentry() {
    ItemEfficiency far =
        judge(
            "ward_sentry",
            bought("item_ward_sentry", 8),
            List.of(ward(0, WardKind.SENTRY, 50, null, WardRemovalKind.UNKNOWN, null)),
            INVISIBLE_KILL,
            trackAt(0, 0),
            trackAt(6000, 0),
            NO_SOURCES);
    ItemEfficiency gone =
        judge(
            "ward_sentry",
            bought("item_ward_sentry", 8),
            List.of(ward(0, WardKind.SENTRY, 20, 80L, WardRemovalKind.KILLED, ENEMY)),
            INVISIBLE_KILL,
            trackAt(0, 0),
            trackAt(0, 0),
            NO_SOURCES);

    assertThat(far.figures()).containsEntry("visionInvisibleKills", 0.0);
    assertThat(gone.figures()).containsEntry("visionInvisibleKills", 0.0);
  }

  @Test
  void anEnemyWardRemovedWhileTheSentryWasUpPaysItsBounty() {
    ItemEfficiency row =
        judge(
            "ward_sentry",
            bought("item_ward_sentry", 8),
            List.of(
                ward(0, WardKind.SENTRY, 50, null, WardRemovalKind.UNKNOWN, null),
                ward(128, WardKind.OBSERVER, 60, 200L, WardRemovalKind.KILLED, ME),
                ward(128, WardKind.OBSERVER, 60, 300L, WardRemovalKind.EXPIRED, ENEMY)),
            List.of(),
            trackAt(0, 0),
            trackAt(0, 0),
            NO_SOURCES);

    assertThat(row.figures())
        .containsEntry("visionDewards", 1.0)
        .containsEntry("visionDewardBounty", 50.0);
    assertThat(row.gain()).isEqualTo(50.0);
    assertThat(VisionKindWording.line(row)).contains("1 enemy ward removed");
  }

  @Test
  void aSmallSpendHasNoRatio() {
    ItemEfficiency row =
        judge(
            "ward_sentry",
            bought("item_ward_sentry", 2),
            List.of(ward(0, WardKind.SENTRY, 50, null, WardRemovalKind.UNKNOWN, null)),
            INVISIBLE_KILL,
            trackAt(2000, 0),
            trackAt(0, 0),
            NO_SOURCES);

    assertThat(row.ratio()).isNull();
    assertThat(row.gain()).isEqualTo(100.0);
  }

  @Test
  void sentriesBoughtAndNeverPlacedAreFlagged() {
    ItemEfficiency row =
        judge(
            "ward_sentry",
            bought("item_ward_sentry", 2),
            List.of(ward(1, WardKind.OBSERVER, 50, null, WardRemovalKind.UNKNOWN, null)),
            List.of(),
            trackAt(0, 0),
            trackAt(0, 0),
            NO_SOURCES);

    assertThat(row.flags()).contains(VisionAspect.NEVER_PLACED);
  }

  @Test
  void aMatchWithoutAWardLogDoesNotFlagNeverPlaced() {
    ItemEfficiency row =
        judge(
            "ward_sentry",
            bought("item_ward_sentry", 2),
            List.of(),
            List.of(),
            trackAt(0, 0),
            trackAt(0, 0),
            NO_SOURCES);

    assertThat(row.flags()).doesNotContain(VisionAspect.NEVER_PLACED);
    assertThat(row.figures()).doesNotContainKey("visionPlaced");
  }

  @Test
  void observersTellTheirLifeAndTheOnesDewardedWithinAMinute() {
    ItemEfficiency row =
        judge(
            "ward_observer",
            bought("item_ward_observer", 3),
            List.of(
                ward(0, WardKind.OBSERVER, 100, 140L, WardRemovalKind.KILLED, ENEMY),
                ward(0, WardKind.OBSERVER, 200, 560L, WardRemovalKind.EXPIRED, ME),
                ward(0, WardKind.OBSERVER, 300, null, WardRemovalKind.UNKNOWN, null)),
            INVISIBLE_KILL,
            trackAt(0, 0),
            trackAt(0, 0),
            NO_SOURCES);

    assertThat(row.figures())
        .containsEntry("visionPlaced", 3.0)
        .containsEntry("visionObserverLifeSeconds", 200.0)
        .containsEntry("visionDewardedFast", 1.0)
        .doesNotContainKey("visionInvisibleKills");
    assertThat(row.flags()).containsExactly(VisionAspect.DEWARDED_FAST);
    assertThat(row.ratio()).isNull();
    assertThat(VisionKindWording.line(row))
        .isEqualTo(
            "3 observers placed, lived 3:20 on average; 1 observer dewarded within a minute");
  }

  @Test
  void dustUsedNearAnInvisibleEnemyWhoDiedEarnsAShareAndDustNeverUsedIsFlagged() {
    List<CombatEvent> events = new ArrayList<>(INVISIBLE_KILL);
    events.add(dustUse(97));

    ItemEfficiency used =
        judge(
            "dust",
            bought("item_dust", 4),
            List.of(),
            events,
            trackAt(500, 0),
            trackAt(0, 0),
            NO_SOURCES);
    ItemEfficiency unused =
        judge(
            "dust",
            bought("item_dust", 4),
            List.of(),
            INVISIBLE_KILL,
            trackAt(500, 0),
            trackAt(0, 0),
            NO_SOURCES);

    assertThat(used.figures())
        .containsEntry("visionDustUses", 1.0)
        .containsEntry("visionInvisibleKills", 1.0);
    assertThat(used.flags()).doesNotContain(VisionAspect.DUST_UNUSED);
    assertThat(used.cost()).isEqualTo(320);
    assertThat(unused.flags()).contains(VisionAspect.DUST_UNUSED);
    assertThat(unused.figures()).containsEntry("visionInvisibleKills", 0.0);
  }

  @Test
  void aGemHolderNearAnInvisibleEnemyWhoDiedEarnsAShare() {
    var changes = bought("item_gem", 1);

    ItemEfficiency near =
        judge(
            "gem", changes, List.of(), INVISIBLE_KILL, trackAt(600, 0), trackAt(0, 0), NO_SOURCES);
    ItemEfficiency far =
        judge(
            "gem", changes, List.of(), INVISIBLE_KILL, trackAt(3000, 0), trackAt(0, 0), NO_SOURCES);

    assertThat(near.figures()).containsEntry("visionInvisibleKills", 1.0);
    assertThat(near.cost()).isEqualTo(900);
    assertThat(far.figures()).containsEntry("visionInvisibleKills", 0.0);
    assertThat(VisionKindWording.line(near)).startsWith("gem held, 1 Riki kill");
  }

  @Test
  void aStackBoughtIntoAnExistingStackAndAFirstStackOfTwoAreCounted() {
    var changes =
        List.of(
            change(10, StateChangeKind.ITEM_ADDED, "item_ward_sentry"),
            ItemTestData.charges(10, "item_ward_sentry", 2),
            ItemTestData.charges(100, "item_ward_sentry", 1),
            ItemTestData.charges(200, "item_ward_sentry", 3));

    ItemEfficiency row =
        judge(
            "ward_sentry", changes, List.of(), List.of(), trackAt(0, 0), trackAt(0, 0), NO_SOURCES);

    assertThat(row.figures()).containsEntry("visionBought", 4.0);
    assertThat(row.cost()).isEqualTo(200);
  }

  @Test
  void anItemPickedUpAgainSoonAfterLettingGoIsNotBoughtTwice() {
    var changes =
        List.of(
            change(10, StateChangeKind.ITEM_ADDED, "item_ward_sentry"),
            change(20, StateChangeKind.ITEM_REMOVED, "item_ward_sentry"),
            change(25, StateChangeKind.ITEM_ADDED, "item_ward_sentry"),
            ItemTestData.charges(25, "item_ward_sentry", 1),
            change(300, StateChangeKind.ITEM_ADDED, "item_ward_sentry"));

    ItemEfficiency row =
        judge(
            "ward_sentry", changes, List.of(), List.of(), trackAt(0, 0), trackAt(0, 0), NO_SOURCES);

    assertThat(row.figures()).containsEntry("visionBought", 2.0);
  }

  @Test
  void aDewardedWardPaysOnlyTheSentryPlacedLatest() {
    List<WardEventDomain> wards =
        List.of(
            ward(0, WardKind.SENTRY, 50, null, WardRemovalKind.UNKNOWN, null),
            ward(1, WardKind.SENTRY, 70, null, WardRemovalKind.UNKNOWN, null),
            ward(128, WardKind.OBSERVER, 60, 200L, WardRemovalKind.KILLED, ME));

    ItemEfficiency first =
        judge(
            "ward_sentry",
            bought("item_ward_sentry", 8),
            wards,
            List.of(),
            trackAt(0, 0),
            trackAt(0, 0),
            NO_SOURCES);

    assertThat(first.figures()).containsEntry("visionDewards", 0.0);
  }

  @Test
  void theLineSaysNothingOfPlacedWardsWithoutAWardLog() {
    ItemEfficiency row =
        judge(
            "ward_sentry",
            bought("item_ward_sentry", 2),
            List.of(),
            List.of(),
            trackAt(0, 0),
            trackAt(0, 0),
            NO_SOURCES);

    assertThat(VisionKindWording.line(row)).isEqualTo("2 sentries bought");
  }
}
