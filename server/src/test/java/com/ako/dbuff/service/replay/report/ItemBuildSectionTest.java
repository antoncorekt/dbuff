package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.report.chart.SkillIconImages;
import com.ako.dbuff.service.replay.report.chart.TableRenderer;
import com.ako.dbuff.service.replay.report.chart.TableSpec;
import com.ako.dbuff.service.replay.report.chart.TableSpec.Cell;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemBuildSectionTest {
  private static final byte[] PNG = {1, 2, 3};

  private final ItemRepository itemRepo = mock(ItemRepository.class);
  private final PlayerGameStatisticRepo stats = mock(PlayerGameStatisticRepo.class);
  private final ConstantsManagers constants = mock(ConstantsManagers.class);
  private final HeroKits kits = mock(HeroKits.class);
  private final SkillIconImages icons = mock(SkillIconImages.class);
  private final TableRenderer tables = mock(TableRenderer.class);
  private final Map<String, ItemConstant> catalog = new HashMap<>();
  private final ItemBuildSection section =
      new ItemBuildSection(itemRepo, stats, constants, kits, icons, tables);

  ItemBuildSectionTest() {
    when(tables.render(any())).thenReturn(Optional.of(PNG));
    when(icons.item(any())).thenReturn(Optional.empty());
    when(icons.hero(anyLong())).thenReturn(Optional.empty());
    when(kits.hero(anyLong())).thenReturn(Optional.empty());
    when(constants.getItemConstantMap()).thenReturn(catalog);
    when(stats.findAllByMatchId(anyLong())).thenReturn(List.of());
  }

  private void item(String key, String dname, long cost, List<String> components) {
    catalog.put(key, ItemConstant.builder().dname(dname).cost(cost).components(components).build());
  }

  private static ItemDomain bought(String key, long seconds) {
    return ItemDomain.builder()
        .playerSlot(0L)
        .itemName(key)
        .itemPurchaseTime(seconds)
        .isNeutral(false)
        .build();
  }

  private static ReplayReportInput anton() {
    return new ReplayReportInput(1L, null, java.util.Set.of(0L), Map.of(0L, "Anton"), 3);
  }

  private void noPicture() {
    when(tables.render(any())).thenReturn(Optional.empty());
  }

  @Test
  void lines_leaveOutComponentsAndConsumablesAndNeutralItems() {
    noPicture();
    item("blink", "Blink Dagger", 2250, List.of("blink_part"));
    item("branches", "Iron Branch", 50, null);
    item("tango", "Tango", 90, null);
    item("enchanted", "Neutral Thing", 3000, null);
    ItemDomain neutral = bought("enchanted", 900);
    neutral.setNeutral(true);
    when(itemRepo.findAllByMatchId(1L))
        .thenReturn(
            List.of(bought("blink", 725), bought("branches", -10), bought("tango", 30), neutral));

    assertThat(section.lines(anton()))
        .contains("Anton: Blink Dagger 12:05")
        .noneMatch(l -> l.contains("Iron") || l.contains("Tango") || l.contains("Neutral"));
  }

  @Test
  void lines_capAtEightKeepingTheCostlyOnesInTimeOrderAndPrintStartBeforeTheHorn() {
    noPicture();
    List<ItemDomain> rows = new ArrayList<>();
    for (int i = 1; i <= 9; i++) {
      item("i" + i, "Item" + i, 2000 + i * 100L, null);
      rows.add(bought("i" + i, 600 - i * 10L));
    }
    item("cheap", "Cheap", 2000, null);
    rows.add(bought("cheap", -5));
    when(itemRepo.findAllByMatchId(1L)).thenReturn(rows);

    String line = section.lines(anton()).get(1);
    assertThat(line)
        .isEqualTo(
            "Anton: Item9 8:30, Item8 8:40, Item7 8:50, Item6 9:00, Item5 9:10, Item4 9:20,"
                + " Item3 9:30, Item2 9:40");
  }

  @Test
  void lines_printStartForAPreHornPurchase() {
    noPicture();
    item("bottle", "Bottle", 2000, null);
    when(itemRepo.findAllByMatchId(1L)).thenReturn(List.of(bought("bottle", -80)));

    assertThat(section.lines(anton())).contains("Anton: Bottle start");
  }

  @Test
  void lines_nameTheHeroAndLeaveOutAPlayerWithNoShownItem() {
    noPicture();
    item("blink", "Blink Dagger", 2250, null);
    item("tango", "Tango", 90, null);
    when(itemRepo.findAllByMatchId(1L)).thenReturn(List.of(bought("blink", 60)));
    when(stats.findAllByMatchId(1L))
        .thenReturn(
            List.of(PlayerMatchStatisticDomain.builder().playerSlot(0L).heroId(5L).build()));
    when(kits.hero(5L))
        .thenReturn(Optional.of(HeroConstant.builder().localized_name("Axe").build()));
    ReplayReportInput two =
        new ReplayReportInput(
            1L, null, java.util.Set.of(0L, 1L), Map.of(0L, "Anton", 1L, "Bob"), 3);

    assertThat(section.lines(two))
        .contains("Anton (Axe): Blink Dagger 1:00")
        .noneMatch(l -> l.startsWith("Bob"));
  }

  @Test
  void lines_doNotRepeatTheHeroWhenTheNameAlreadyCarriesIt() {
    noPicture();
    item("blink", "Blink Dagger", 2250, null);
    when(itemRepo.findAllByMatchId(1L)).thenReturn(List.of(bought("blink", 60)));
    when(stats.findAllByMatchId(1L))
        .thenReturn(
            List.of(PlayerMatchStatisticDomain.builder().playerSlot(0L).heroId(5L).build()));
    when(kits.hero(5L))
        .thenReturn(Optional.of(HeroConstant.builder().localized_name("Axe").build()));
    ReplayReportInput in =
        new ReplayReportInput(1L, null, java.util.Set.of(0L), Map.of(0L, "Anton (Axe)"), 3);

    assertThat(section.lines(in)).contains("Anton (Axe): Blink Dagger 1:00");
  }

  @Test
  void image_drawsOneRowWithTimeTextAndNameWhereTheIconIsMissing() {
    item("blink", "Blink Dagger", 2250, null);
    item("bkb", "Black King Bar", 4050, null);
    when(icons.item("bkb")).thenReturn(Optional.of(new byte[] {9}));
    when(itemRepo.findAllByMatchId(1L))
        .thenReturn(List.of(bought("blink", 725), bought("bkb", 1500)));

    assertThat(section.image(anton()))
        .hasValueSatisfying(i -> assertThat(i.name()).isEqualTo("item-build-1.png"));
    ArgumentCaptor<TableSpec> spec = ArgumentCaptor.forClass(TableSpec.class);
    verify(tables).render(spec.capture());
    assertThat(spec.getValue().title()).isEqualTo("Item builds");
    List<Cell> row = spec.getValue().rows().get(0);
    assertThat(row.get(1).text()).isEqualTo("12:05");
    assertThat(row.get(1).sub()).isEqualTo("Blink Dagger");
    assertThat(row.get(2).text()).isEqualTo("25:00");
    assertThat(row.get(2).sub()).isNull();
  }

  @Test
  void lines_areEmptyWhenNobodyBoughtAShownItem() {
    item("tango", "Tango", 90, null);
    when(itemRepo.findAllByMatchId(1L)).thenReturn(List.of(bought("tango", 30)));

    assertThat(section.lines(anton())).isEmpty();
    assertThat(section.view(anton())).isEmpty();
  }
}
