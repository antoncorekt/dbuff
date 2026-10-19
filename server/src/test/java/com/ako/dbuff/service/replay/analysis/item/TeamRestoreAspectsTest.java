package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.StateChangeKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.change;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.sampleOf;
import static com.ako.dbuff.service.replay.analysis.item.ItemTestData.use;
import static org.assertj.core.api.Assertions.assertThat;

class TeamRestoreAspectsTest {
  private static final String MEKANSM = "item_mekansm";
  private static final String BOOTS = "item_arcane_boots";
  private static final String LION = "npc_dota_hero_lion";
  private static final String MAIDEN = "npc_dota_hero_crystal_maiden";
  private static final String FAR = "npc_dota_hero_juggernaut";
  private static final String NEAR = "npc_dota_hero_sniper";
  private static final int SAMPLES = 150;

  private static final ItemKits KITS =
      key ->
          Optional.of(
              ItemConstant.builder()
                  .cost("mekansm".equals(key) ? 1775L : 1500L)
                  .attrib(
                      "mekansm".equals(key)
                          ? List.of(Map.of("key", "heal_radius", "value", "1200"))
                          : List.of(
                              Map.of("key", "replenish_radius", "value", "1200"),
                              Map.of("key", "replenish_amount", "value", "150")))
                  .build());

  private final List<ItemAspect> aspects = List.of(new TeamRestoreAspect(), new SaveAspect());

  private static ItemEfficiencyEntry entry(String key, String item, List<String> aspectNames) {
    return ItemTestData.entry(key, aspectNames, List.of(HeroInputs.itemKey(item)), Map.of());
  }

  private static ReplayPlayerTrackDomain trackAt(int x, int y) {
    ReplayPlayerTrackDomain track = ItemTestData.track(SAMPLES);
    track.setX(new int[SAMPLES]);
    track.setY(new int[SAMPLES]);
    Arrays.fill(track.getX(), x);
    Arrays.fill(track.getY(), y);
    track.setFlags(new int[SAMPLES]);
    Arrays.fill(track.getFlags(), 1);
    track.setMaxHp(new int[SAMPLES]);
    Arrays.fill(track.getMaxHp(), 1000);
    return track;
  }

  private static HeroInputs ally(
      long slot, String name, ReplayPlayerTrackDomain track, List<CombatEvent> events) {
    return new HeroInputs(slot, name, List.of(), events, track, List.of(), List.of());
  }

  private static CombatEvent heal(double second, String item, String target, int amount) {
    return new CombatEvent(
        CombatEventKind.HEAL,
        second,
        ItemTestData.HERO,
        target,
        item,
        CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
        amount,
        0,
        0,
        0,
        0,
        0,
        0);
  }

  private static CombatEvent hitOn(String target, double second, int damage, int healthAfter) {
    return new CombatEvent(
        CombatEventKind.DAMAGE,
        second,
        "npc_dota_hero_pudge",
        target,
        "",
        CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
        damage,
        healthAfter,
        0,
        0,
        0,
        0,
        0);
  }

  private static CombatEvent deathOf(String target, double second) {
    return new CombatEvent(
        CombatEventKind.DEATH,
        second,
        "npc_dota_hero_pudge",
        target,
        "",
        CombatEvent.ATTACKER_HERO | CombatEvent.TARGET_HERO,
        0,
        0,
        0,
        0,
        0,
        0,
        0);
  }

  private List<ItemEfficiency> judge(
      ItemEfficiencyEntry entry,
      String item,
      List<CombatEvent> userEvents,
      List<HeroInputs> others) {
    List<HeroInputs> roster = new ArrayList<>();
    List<CombatEvent> matchEvents =
        new ArrayList<>(userEvents.stream().filter(e -> e.kind() == CombatEventKind.HEAL).toList());
    MatchFacts match =
        new MatchFacts(
            matchEvents,
            Map.of(),
            Map.of(
                1L,
                List.of(
                    ItemTestData.minute(1, 600, 0),
                    ItemTestData.minute(2, 1200, 0),
                    ItemTestData.minute(3, 1800, 0),
                    ItemTestData.minute(4, 2400, 0))),
            () -> roster);
    HeroInputs user =
        ItemTestData.heroWith(
            ItemTestData.hero(
                List.of(change(-60, StateChangeKind.ITEM_ADDED, item)),
                userEvents.stream().filter(e -> e.kind() != CombatEventKind.HEAL).toList(),
                trackAt(0, 0),
                List.of(),
                List.of()),
            match);
    roster.add(user);
    others.forEach(
        o ->
            roster.add(
                new HeroInputs(
                    o.slot(),
                    o.hero(),
                    o.changes(),
                    o.events(),
                    o.track(),
                    o.creepKills(),
                    o.activity(),
                    match,
                    0)));
    return ItemTestData.judge(entry, aspects, KITS, 600, List.of(user));
  }

  private static ReplayPlayerTrackDomain far() {
    return trackAt(5000, 0);
  }

  @Test
  void aUseCountsTheAlliesTheLogHealedAndThoseAliveWithinTheRadius() {
    List<HeroInputs> others =
        List.of(
            ally(1, LION, trackAt(600, 0), List.of()),
            ally(2, MAIDEN, trackAt(900, 0), List.of()),
            ally(3, FAR, far(), List.of()),
            ally(4, NEAR, trackAt(300, 0), List.of()),
            ally(128, "npc_dota_hero_enemy", trackAt(100, 0), List.of()));
    ItemEfficiencyEntry entry = entry("mekansm", MEKANSM, List.of(TeamRestoreAspect.NAME));

    List<ItemEfficiency> rows =
        judge(
            entry,
            MEKANSM,
            List.of(
                use(200.0, MEKANSM, ""),
                heal(200.0, MEKANSM, LION, 250),
                heal(200.0, MEKANSM, MAIDEN, 250),
                heal(200.0, "item_magic_wand", NEAR, 99)),
            others);

    assertThat(rows).hasSize(1);
    ItemEfficiency row = rows.getFirst();
    assertThat(row.count()).isEqualTo(1);
    assertThat(row.figures())
        .containsEntry("hp", 500.0)
        .containsEntry("mana", 0.0)
        .containsEntry("allies", 3.0);
    assertThat(row.flags()).doesNotContain(TeamRestoreAspect.MANA_ESTIMATED);
    assertThat(row.gain()).isEqualTo(500 * 0.3);
    assertThat(row.rows().getFirst().figures()).containsEntry("allies", 3.0);
  }

  @Test
  void aDeadAllyIsNotReachedByPosition() {
    ReplayPlayerTrackDomain dead = trackAt(100, 0);
    Arrays.fill(dead.getFlags(), 0);
    ItemEfficiency row =
        judge(
                entry("mekansm", MEKANSM, List.of(TeamRestoreAspect.NAME)),
                MEKANSM,
                List.of(use(200.0, MEKANSM, "")),
                List.of(ally(1, LION, dead, List.of())))
            .getFirst();

    assertThat(row.figures()).containsEntry("allies", 0.0);
  }

  @Test
  void manaIsEstimatedFromTheConstantsAndCappedByWhatTheAllyWasMissing() {
    ReplayPlayerTrackDomain missing = trackAt(300, 0);
    missing.getMana()[sampleOf(200)] = 300;
    missing.getManaPercent()[sampleOf(200)] = 75;
    List<HeroInputs> others =
        List.of(ally(1, LION, missing, List.of()), ally(2, MAIDEN, trackAt(400, 0), List.of()));

    ItemEfficiency row =
        judge(
                entry("arcane_boots", BOOTS, List.of(TeamRestoreAspect.NAME)),
                BOOTS,
                List.of(use(200.0, BOOTS, "")),
                others)
            .getFirst();

    assertThat(row.figures()).containsEntry("mana", 100.0 + 0.0).containsEntry("allies", 2.0);
    assertThat(row.flags()).containsExactly(TeamRestoreAspect.MANA_ESTIMATED);
    assertThat(row.gain()).isEqualTo(100 * 0.3);
  }

  @Test
  void anAllyAtLowHealthUnderHeavyDamageWhoLivesIsASaveWorthHisDeath() {
    ReplayPlayerTrackDomain lion = trackAt(300, 0);
    List<CombatEvent> lionEvents =
        List.of(hitOn(LION, 199.0, 600, 150), hitOn(LION, 201.0, 100, 300));

    ItemEfficiency row =
        judge(
                entry("mekansm", MEKANSM, List.of(TeamRestoreAspect.NAME, SaveAspect.NAME)),
                MEKANSM,
                List.of(use(200.0, MEKANSM, ""), heal(200.0, MEKANSM, LION, 250)),
                List.of(ally(1, LION, lion, lionEvents)))
            .getFirst();

    assertThat(row.figures()).containsEntry("saves", 1.0).containsEntry("diedSoon", 0.0);
    assertThat(row.rows().getFirst().figures())
        .containsEntry("saved", 1.0)
        .containsEntry("hp", 250.0);
    assertThat(row.flags()).contains(SaveAspect.DEATH_COST_ESTIMATED);
    assertThat(row.gain()).isGreaterThan(250 * 0.3);
  }

  @Test
  void anAllyWhoDiesSoonAfterTheUseIsNotASave() {
    List<CombatEvent> lionEvents =
        List.of(hitOn(LION, 199.0, 600, 150), hitOn(LION, 202.0, 150, 0), deathOf(LION, 202.0));

    ItemEfficiency row =
        judge(
                entry("mekansm", MEKANSM, List.of(TeamRestoreAspect.NAME, SaveAspect.NAME)),
                MEKANSM,
                List.of(use(200.0, MEKANSM, ""), heal(200.0, MEKANSM, LION, 250)),
                List.of(ally(1, LION, trackAt(300, 0), lionEvents)))
            .getFirst();

    assertThat(row.figures()).containsEntry("saves", 0.0).containsEntry("diedSoon", 1.0);
  }

  @Test
  void anItemNeverUsedHasNoUsesAndNothingToJudge() {
    ItemEfficiency row =
        judge(
                entry("mekansm", MEKANSM, List.of(TeamRestoreAspect.NAME, SaveAspect.NAME)),
                MEKANSM,
                List.of(),
                List.of(ally(1, LION, trackAt(300, 0), List.of())))
            .getFirst();

    assertThat(row.count()).isZero();
    assertThat(row.gain()).isZero();
  }

  @Test
  void theUserGetsTheRestoreToo_withoutBeingAnAllyOrASave() {
    ReplayPlayerTrackDomain user = trackAt(0, 0);
    user.getMana()[sampleOf(200)] = 300;
    user.getManaPercent()[sampleOf(200)] = 75;
    ItemEfficiency row =
        judge(
                entry("mekansm", MEKANSM, List.of(TeamRestoreAspect.NAME, SaveAspect.NAME)),
                MEKANSM,
                List.of(
                    use(200.0, MEKANSM, ""),
                    heal(200.0, MEKANSM, ItemTestData.HERO, 250),
                    heal(200.0, MEKANSM, LION, 250)),
                List.of(ally(1, LION, trackAt(300, 0), List.of())))
            .getFirst();

    assertThat(row.figures()).containsEntry("hp", 500.0).containsEntry("allies", 1.0);
    assertThat(row.figures()).containsEntry("saves", 0.0);
  }

  @Test
  void anAllyTheItemDidNotHealIsNoSaveEvenAtLowHealthUnderHeavyDamage() {
    List<CombatEvent> lionEvents =
        List.of(hitOn(LION, 199.0, 600, 150), hitOn(LION, 201.0, 100, 300));

    ItemEfficiency row =
        judge(
                entry("mekansm", MEKANSM, List.of(TeamRestoreAspect.NAME, SaveAspect.NAME)),
                MEKANSM,
                List.of(use(200.0, MEKANSM, "")),
                List.of(ally(1, LION, trackAt(300, 0), lionEvents)))
            .getFirst();

    assertThat(row.figures()).containsEntry("allies", 1.0).containsEntry("saves", 0.0);
  }

  @Test
  void aMissingRadiusInTheConstantsIsFlagged() {
    ItemKits none = key -> Optional.of(ItemConstant.builder().cost(100L).attrib(List.of()).build());
    ItemEfficiencyEntry entry = entry("mekansm", MEKANSM, List.of(TeamRestoreAspect.NAME));
    HeroInputs user =
        ItemTestData.hero(
            List.of(change(-60, StateChangeKind.ITEM_ADDED, MEKANSM)),
            List.of(use(200.0, MEKANSM, "")),
            trackAt(0, 0),
            List.of(),
            List.of());

    ItemEfficiency row = ItemTestData.judge(entry, aspects, none, 600, List.of(user)).getFirst();

    assertThat(row.flags()).contains(TeamRestoreAspect.RADIUS_ASSUMED);
  }

  @Test
  void theAttributeKeysAreTheOnesTheRealConstantsUse() throws Exception {
    Map<String, Map<String, Object>> items =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(
                getClass().getResourceAsStream("/replay/team-restore-constants.json"),
                new com.fasterxml.jackson.core.type.TypeReference<>() {});
    ItemKits kits =
        key ->
            Optional.of(items.get(key))
                .map(
                    item ->
                        ItemConstant.builder()
                            .cost(((Number) item.get("cost")).longValue())
                            .attrib((List<Map<String, String>>) item.get("attrib"))
                            .build());

    for (String key : items.keySet()) {
      assertThat(TeamUses.RADIUS_ATTRIBUTES.stream().map(a -> kits.attribute(key, a)))
          .as("%s radius", key)
          .anyMatch(java.util.OptionalDouble::isPresent);
    }
    assertThat(TeamUses.MANA_ATTRIBUTES.stream().map(a -> kits.attribute("arcane_boots", a)))
        .anyMatch(java.util.OptionalDouble::isPresent);
    assertThat(TeamUses.MANA_ATTRIBUTES.stream().map(a -> kits.attribute("guardian_greaves", a)))
        .anyMatch(java.util.OptionalDouble::isPresent);
    assertThat(kits.attribute("holy_locket", TeamUses.MANA_PER_CHARGE)).isPresent();
  }
}
