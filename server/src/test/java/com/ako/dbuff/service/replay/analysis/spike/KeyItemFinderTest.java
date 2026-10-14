package com.ako.dbuff.service.replay.analysis.spike;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.spike.KeyItemFinder.Completion;
import com.ako.dbuff.service.replay.analysis.spike.KeyItemFinder.FightActivity;
import com.ako.dbuff.service.replay.analysis.spike.KeyItemFinder.Swing;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyItemFinderTest {

  private static final Map<String, Long> COSTS =
      Map.of(
          "black_king_bar", 3975L,
          "blink", 2250L,
          "echo_sabre", 2500L,
          "force_staff", 2200L,
          "magic_wand", 450L);

  private final SpikeProperties properties = new SpikeProperties();
  private final KeyItemFinder finder =
      new KeyItemFinder(
          key ->
              Optional.ofNullable(COSTS.get(key)).map(c -> ItemConstant.builder().cost(c).build()),
          properties);

  private static Completion done(long slot, String item, double seconds) {
    return new Completion(slot, item, seconds, COSTS.get(item));
  }

  private static ReplayStateChangeDomain added(long slot, String name, double seconds) {
    return ReplayStateChangeDomain.builder()
        .playerSlot(slot)
        .name(name)
        .gameSeconds(seconds)
        .kind("ITEM_ADDED")
        .build();
  }

  /** A swing at 1000 s for Radiant with one fight at 1010 s in which slots 0 and 1 were active. */
  private static Swing swing() {
    return new Swing(1000, TeamSide.RADIANT, List.of(new FightActivity(1010, Set.of(0L, 1L))));
  }

  @Test
  void find_picksTheMostExpensiveItemOfAHeroActiveInTheFight() {
    List<Completion> items =
        List.of(done(0, "blink", 900), done(1, "black_king_bar", 850), done(0, "force_staff", 950));

    assertThat(finder.find(items, swing())).contains(done(1, "black_king_bar", 850));
  }

  @Test
  void find_breaksCostTiesByEarlierTimeThenLowerSlot() {
    assertThat(finder.find(List.of(done(1, "blink", 900), done(0, "blink", 800)), swing()))
        .contains(done(0, "blink", 800));
    assertThat(finder.find(List.of(done(1, "blink", 900), done(0, "blink", 900)), swing()))
        .contains(done(0, "blink", 900));
  }

  @Test
  void find_ignoresItemsOutsideTheWindow() {
    List<Completion> items =
        List.of(done(0, "black_king_bar", 759), done(0, "blink", 1001), done(1, "echo_sabre", 760));

    assertThat(finder.find(items, swing())).contains(done(1, "echo_sabre", 760));
  }

  @Test
  void find_ignoresItemsBoughtAfterTheFirstFightBegan() {
    Swing early = new Swing(1000, TeamSide.RADIANT, List.of(new FightActivity(970, Set.of(0L))));

    assertThat(finder.find(List.of(done(0, "black_king_bar", 980)), early)).isEmpty();
    assertThat(finder.find(List.of(done(0, "black_king_bar", 969)), early)).isPresent();
  }

  @Test
  void find_needsTheOwnerToBeActiveAndOnTheFavouredTeam() {
    List<Completion> items = List.of(done(2, "black_king_bar", 900), done(128, "blink", 900));

    assertThat(finder.find(items, swing())).isEmpty();
  }

  @Test
  void find_isEmptyWithoutAFightOrAnItem() {
    assertThat(
            finder.find(
                List.of(done(0, "blink", 900)), new Swing(1000, TeamSide.RADIANT, List.of())))
        .isEmpty();
    assertThat(finder.find(List.of(), swing())).isEmpty();
  }

  @Test
  void find_takesTheActivityOfAnyFightOfTheSwing() {
    Swing two =
        new Swing(
            1000,
            TeamSide.RADIANT,
            List.of(new FightActivity(1010, Set.of(0L)), new FightActivity(1100, Set.of(2L))));

    assertThat(finder.find(List.of(done(2, "blink", 900)), two)).isPresent();
  }

  @Test
  void completions_keepOnlyTheFirstGainOfAnExpensiveItem() {
    List<ReplayStateChangeDomain> changes =
        List.of(
            added(0, "item_magic_wand", 100),
            added(0, "item_blink", 900),
            added(0, "item_blink", 950),
            added(1, "item_blink", 920),
            added(0, "item_unknown_thing", 10),
            ReplayStateChangeDomain.builder()
                .playerSlot(0L)
                .name("item_black_king_bar")
                .gameSeconds(5.0)
                .kind("ITEM_REMOVED")
                .build());

    assertThat(finder.completions(changes))
        .containsExactly(done(0, "blink", 900), done(1, "blink", 920));
  }

  @Test
  void completions_respectTheMinimumCost() {
    properties.setKeyItemMinCost(2300);

    assertThat(
            finder.completions(
                List.of(added(0, "item_blink", 900), added(0, "item_echo_sabre", 910))))
        .containsExactly(done(0, "echo_sabre", 910));
  }

  @Test
  void properties_failAtStartupOnABadValue() {
    SpikeProperties bad = new SpikeProperties();
    bad.setKeyItemMinCost(Double.NaN);
    org.assertj.core.api.Assertions.assertThatThrownBy(bad::validate)
        .hasMessageContaining("key-item-min-cost");
    SpikeProperties negative = new SpikeProperties();
    negative.setKeyItemMinCost(-1);
    org.assertj.core.api.Assertions.assertThatThrownBy(negative::validate)
        .hasMessageContaining("key-item-min-cost");
    SpikeProperties window = new SpikeProperties();
    window.setKeyItemWindowSeconds(0);
    org.assertj.core.api.Assertions.assertThatThrownBy(window::validate)
        .hasMessageContaining("key-item-window-seconds");
    new SpikeProperties().validate();
  }
}
