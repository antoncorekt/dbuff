package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.BuybackEventDomain;
import com.ako.dbuff.dao.model.DeathEventDomain;
import com.ako.dbuff.dao.model.ItemEfficiencyDomain;
import com.ako.dbuff.dao.model.ItemPurchaseEventDomain;
import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.DeathEventRepo;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.dao.repo.ItemPurchaseEventRepo;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyJson;
import com.ako.dbuff.service.replay.analysis.item.ItemKits;
import com.ako.dbuff.service.replay.analysis.item.MidasUsesAspect;
import com.ako.dbuff.service.replay.report.chart.ChartPalette;
import com.ako.dbuff.service.replay.report.chart.ChartSpec;
import com.ako.dbuff.service.replay.report.chart.ChartSpec.Pin;
import com.ako.dbuff.service.replay.report.chart.ChartSpec.PinShape;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * What happened to a hero that the income graph marks: deaths, buybacks, the whitelisted items he
 * bought (with the point where a Hand of Midas paid for itself) and his biggest other purchases. A
 * row that cannot be read is left out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncomePins {
  static final String MIDAS_KIND = MidasUsesAspect.NAME;
  private static final Set<String> MIDAS_KINDS = Set.of(MIDAS_KIND, "hand-of-midas");
  private static final int BIG_PURCHASES = 2;
  private static final long BIG_PURCHASE_COST = 2000;
  private static final double SAME_PURCHASE_SECONDS = 5;
  private static final int MAX_LABEL = 22;
  private static final String RECIPE_PREFIX = "recipe_";

  private final DeathEventRepo deathRepo;
  private final BuybackEventRepo buybackRepo;
  private final ItemEfficiencyRepo itemRepo;
  private final ItemPurchaseEventRepo purchaseRepo;
  private final ItemKits kits;
  private final AbilityLabels labels;
  private final ItemKindWordings wordings;

  /** The stored events of one match, read once. */
  public Events read(long matchId) {
    List<DeathEventDomain> deaths = orEmpty(deathRepo.findByMatchId(matchId));
    List<BuybackEventDomain> buybacks = orEmpty(buybackRepo.findByMatchId(matchId));
    List<ItemPurchaseEventDomain> purchases = orEmpty(purchaseRepo.findByMatchId(matchId));
    return new Events(deaths, buybacks, efficiency(matchId), purchases);
  }

  private List<ItemEfficiency> efficiency(long matchId) {
    List<ItemEfficiency> rows = new ArrayList<>();
    for (ItemEfficiencyDomain stored : orEmpty(itemRepo.findByMatchId(matchId))) {
      try {
        ItemEfficiency row = ItemEfficiencyJson.fromRow(stored);
        if (row.purchaseSeconds() != null) {
          rows.add(row);
        }
      } catch (RuntimeException e) {
        log.warn(
            "Income graph skips item row {} of match {}: {}",
            stored.getEntryKey(),
            matchId,
            e.toString());
      }
    }
    return rows;
  }

  private static <T> List<T> orEmpty(List<T> rows) {
    return rows == null ? List.of() : rows.stream().filter(Objects::nonNull).toList();
  }

  public final class Events {
    private final List<DeathEventDomain> deaths;
    private final List<BuybackEventDomain> buybacks;
    private final List<ItemEfficiency> items;
    private final List<ItemPurchaseEventDomain> purchases;

    private Events(
        List<DeathEventDomain> deaths,
        List<BuybackEventDomain> buybacks,
        List<ItemEfficiency> items,
        List<ItemPurchaseEventDomain> purchases) {
      this.deaths = deaths;
      this.buybacks = buybacks;
      this.items = items;
      this.purchases = purchases;
    }

    /** Every mark of one hero, in no particular order. */
    List<Pin> pins(long slot) {
      List<Pin> pins = new ArrayList<>();
      deaths.stream()
          .filter(d -> d.getPlayerSlot() != null && d.getPlayerSlot() == slot)
          .filter(d -> d.getDeathTime() != null && d.getDeathTime() >= 0)
          .forEach(
              d -> pins.add(new Pin(d.getDeathTime(), PinShape.CROSS, ChartPalette.DEATH, null)));
      buybacks.stream()
          .filter(b -> b.getPlayerSlot() != null && b.getPlayerSlot() == slot)
          .filter(b -> b.getSeconds() != null && b.getSeconds() >= 0)
          .forEach(
              b ->
                  pins.add(
                      new Pin(b.getSeconds(), PinShape.DIAMOND, ChartPalette.BUYBACK, "Buyback")));
      List<ItemEfficiency> his = items.stream().filter(i -> i.slot() == slot).toList();
      for (ItemEfficiency item : his) {
        if (item.purchaseSeconds() >= 0) {
          pins.add(
              new Pin(
                  item.purchaseSeconds(),
                  PinShape.TRIANGLE,
                  ChartPalette.PURCHASE,
                  name(item.itemKey())));
        }
        if (MIDAS_KINDS.contains(item.kind()) && item.paybackSeconds() != null) {
          pins.add(
              new Pin(
                  item.paybackSeconds(),
                  PinShape.STAR,
                  ChartPalette.PAYBACK,
                  name(item.itemKey()) + " paid back"));
        }
      }
      pins.addAll(biggest(slot, his));
      return distinct(pins);
    }

    /** One pin per shape, label and second: a repeated event is drawn once. */
    private static List<Pin> distinct(List<Pin> pins) {
      Set<String> seen = new HashSet<>();
      return pins.stream()
          .filter(p -> seen.add(p.shape() + "|" + p.label() + "|" + Math.round(p.seconds())))
          .toList();
    }

    private List<Pin> biggest(long slot, List<ItemEfficiency> whitelisted) {
      List<ItemPurchaseEventDomain> his =
          purchases.stream()
              .filter(p -> p.getPlayerSlot() != null && p.getPlayerSlot() == slot)
              .filter(
                  p ->
                      p.getItemKey() != null
                          && p.getPurchaseTime() != null
                          && p.getPurchaseTime() >= 0)
              .filter(p -> !p.getItemKey().startsWith(RECIPE_PREFIX))
              .filter(p -> cost(p.getItemKey()) >= BIG_PURCHASE_COST)
              .filter(p -> whitelisted.stream().noneMatch(w -> sameBuy(w, p)))
              .sorted(Comparator.comparingLong(ItemPurchaseEventDomain::getPurchaseTime))
              .toList();
      return unique(his).stream()
          .sorted(
              Comparator.comparingLong((ItemPurchaseEventDomain p) -> cost(p.getItemKey()))
                  .reversed())
          .limit(BIG_PURCHASES)
          .map(
              p ->
                  new Pin(
                      p.getPurchaseTime(),
                      PinShape.TRIANGLE,
                      ChartPalette.PURCHASE,
                      name(p.getItemKey())))
          .toList();
    }

    private static List<ItemPurchaseEventDomain> unique(List<ItemPurchaseEventDomain> inTime) {
      List<ItemPurchaseEventDomain> kept = new ArrayList<>();
      inTime.forEach(p -> addUnlessRepeated(kept, p));
      return kept;
    }

    private static void addUnlessRepeated(
        List<ItemPurchaseEventDomain> kept, ItemPurchaseEventDomain next) {
      boolean repeated =
          kept.stream()
              .anyMatch(
                  k ->
                      k.getItemKey().equals(next.getItemKey())
                          && Math.abs(k.getPurchaseTime() - next.getPurchaseTime())
                              <= SAME_PURCHASE_SECONDS);
      if (!repeated) {
        kept.add(next);
      }
    }

    private boolean sameBuy(ItemEfficiency item, ItemPurchaseEventDomain purchase) {
      return Objects.equals(item.itemKey(), purchase.getItemKey())
          && Math.abs(item.purchaseSeconds() - purchase.getPurchaseTime()) <= SAME_PURCHASE_SECONDS;
    }

    private long cost(String key) {
      OptionalLong cost = kits.cost(key);
      return cost.orElse(0);
    }

    /** What the Hand of Midas of a hero did, in one sentence; empty without one. */
    Optional<String> midasSentence(long slot, Map<Long, String> heroes) {
      return items.stream()
          .filter(i -> i.slot() == slot && MIDAS_KINDS.contains(i.kind()))
          .findFirst()
          .flatMap(
              i ->
                  wordings
                      .find(i.kind())
                      .filter(w -> w.complete(i))
                      .map(
                          w ->
                              w.shortVerdict(
                                  i,
                                  "%s's %s (bought at %s)"
                                      .formatted(
                                          BehaviourWording.hero(heroes, slot),
                                          labels.label("item_" + i.itemKey()),
                                          GameTime.clock(i.purchaseSeconds())))));
    }
  }

  private String name(String itemKey) {
    String label = labels.label("item_" + itemKey);
    return LineText.cut(label.isBlank() ? itemKey : label, MAX_LABEL);
  }

  static boolean has(List<Pin> pins, PinShape shape) {
    return pins.stream().anyMatch(p -> p.shape() == shape);
  }

  static ChartSpec.LegendEntry legend(PinShape shape, String text) {
    return new ChartSpec.LegendEntry(
        switch (shape) {
          case CROSS -> ChartPalette.DEATH;
          case DIAMOND -> ChartPalette.BUYBACK;
          case TRIANGLE -> ChartPalette.PURCHASE;
          case STAR -> ChartPalette.PAYBACK;
        },
        text,
        shape);
  }
}
