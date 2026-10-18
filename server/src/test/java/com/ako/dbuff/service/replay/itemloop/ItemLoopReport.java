package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.looprunner.LoopWording;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopReport;
import com.ako.dbuff.service.replay.report.ItemWording;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The review report of one stored match, as markdown: one row per hero and whitelisted item (what
 * it gave, in words, and the efficiency ratio) and the rows behind them (each use, each payback,
 * each measured window). The verdict columns are left empty for the reviewers.
 */
final class ItemLoopReport {
  static final String ITEMS = "Items";
  static final String DETAILS = "Item details";

  private static final String HOW_TO_READ =
      "\"Items\" has one row per hero and whitelisted item: when he bought it, how often he used"
          + " it, what it gave in words and its efficiency, which is what it gave divided by what"
          + " it cost (3.0x: it gave three times its price; below 1x it did not pay for itself)."
          + " Magic Stick counts health and mana restored (at most what he was missing) at a price"
          + " per point, and a save is a use at low health under heavy damage after which he lived"
          + " 10 more seconds. Hand of Midas counts the gold and experience of its uses. The"
          + " farming items (Battle Fury, Maelstrom, Mjollnir) compare creep kills, creep gold and"
          + " camps per minute in the minutes before and after the first purchase, and add the"
          + " damage to enemy heroes: the item's own damage (chain lightning, cleave, static"
          + " charge) and the rise of the hero's damage per minute from its stats, each valued by"
          + " its share of the kills it helped (a small price for damage to an enemy who lived);"
          + " the result gives the farm part and the fights part of the ratio separately, then"
          + " the total. \"Item details\" lists every use, payback and measured"
          + " stretch. Times are the game clock (m:ss). Write OK or REJECT in the assistant and"
          + " owner columns.";

  record MatchData(
      long matchId,
      MatchDomain match,
      String modeName,
      Map<Long, String> heroNames,
      List<ItemEfficiency> rows,
      Function<String, String> itemNames) {}

  private ItemLoopReport() {}

  static String render(MatchData d) {
    List<ItemEfficiency> rows =
        d.rows().stream()
            .sorted(
                Comparator.comparing(ItemEfficiency::entryKey).thenComparing(ItemEfficiency::slot))
            .toList();
    List<String> out = new ArrayList<>();
    out.add(ReviewLoopReport.matchHeader(d.matchId(), d.modeName(), d.match()));
    out.add("");
    out.addAll(ReviewLoopReport.introduction(HOW_TO_READ, summary(rows)));
    out.add("");
    out.addAll(itemsTable(d, rows));
    out.add("");
    out.addAll(detailsTable(d, rows));
    return String.join("\n", out) + "\n";
  }

  private static String summary(List<ItemEfficiency> rows) {
    if (rows.isEmpty()) {
      return "no whitelisted item bought by any hero (the analysis did not run, or nobody"
          + " bought one).";
    }
    Map<String, Long> perEntry = new java.util.TreeMap<>();
    rows.forEach(r -> perEntry.merge(LoopWording.words(r.entryKey()), 1L, Long::sum));
    double saves = rows.stream().mapToDouble(r -> r.figures().getOrDefault("saves", 0.0)).sum();
    long paidBack = rows.stream().filter(r -> r.paybackSeconds() != null).count();
    long paying = rows.stream().filter(r -> r.ratio() != null && r.ratio() >= 1).count();
    long limited = rows.stream().filter(r -> !r.flags().isEmpty()).count();
    return ("%d hero rows (%s); %d paid for themselves (ratio 1x or more), %d reached their cost at"
            + " a known time, %s saves with a Magic Stick or Wand; rows with a limit of the data"
            + " (named in their result): %d.")
        .formatted(
            rows.size(),
            String.join(
                ", ",
                perEntry.entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).toList()),
            paying,
            paidBack,
            LoopWording.gold(saves),
            limited);
  }

  private static List<String> itemsTable(MatchData d, List<ItemEfficiency> rows) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + ITEMS,
                "",
                "| hero and item | bought | uses | result | efficiency | assistant | owner |",
                "|---|---|---|---|---|---|---|"));
    for (ItemEfficiency r : rows) {
      Wording wording = Wording.of(r.kind());
      out.add(
          "| %s | %s | %s | %s | %s | | |"
              .formatted(
                  ReviewLoopReport.tableCell(label(d, r)),
                  LoopWording.clock(r.purchaseSeconds()),
                  wording.showsUses() ? String.valueOf(r.count()) : "-",
                  ReviewLoopReport.tableCell(wording.result(r, d.itemNames())),
                  ItemWording.ratio(r.ratio())));
    }
    return out;
  }

  private static List<String> detailsTable(MatchData d, List<ItemEfficiency> rows) {
    List<String> out =
        new ArrayList<>(
            List.of(
                "## " + DETAILS,
                "",
                "| row | time | on what | what it gave | evidence | assistant | owner |",
                "|---|---|---|---|---|---|---|"));
    for (ItemEfficiency r : rows) {
      Wording wording = Wording.of(r.kind());
      int number = 0;
      for (Detail detail : r.rows()) {
        number += "USE".equals(detail.label()) ? 1 : 0;
        String[] cells = wording.detail(r, detail);
        out.add(
            "| %s | %s | %s | %s | %s | | |"
                .formatted(
                    ReviewLoopReport.tableCell(rowKey(d, r, detail, number)),
                    cells[0],
                    ReviewLoopReport.tableCell(cells[1]),
                    ReviewLoopReport.tableCell(cells[2]),
                    ReviewLoopReport.tableCell(cells[3])));
      }
    }
    return out;
  }

  private static String label(MatchData d, ItemEfficiency r) {
    return "%s: %s".formatted(LoopWording.hero(d.heroNames(), r.slot()), itemName(d, r));
  }

  private static String itemName(MatchData d, ItemEfficiency r) {
    String name = d.itemNames().apply(r.itemKey());
    return name == null ? LoopWording.words(r.itemKey()) : name;
  }

  private static String rowKey(MatchData d, ItemEfficiency r, Detail detail, int useNumber) {
    String what =
        switch (detail.label()) {
          case "USE" -> "use %d at %s".formatted(useNumber, LoopWording.clock(detail.seconds()));
          case "PAYBACK" -> "paid for itself at " + LoopWording.clock(detail.seconds());
          case "BEFORE" -> "before the purchase";
          case "AFTER" -> "after the purchase";
          default ->
              LoopWording.words(detail.label()) + " at " + LoopWording.clock(detail.seconds());
        };
    return label(d, r) + ", " + what;
  }
}
