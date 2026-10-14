package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ItemEfficiencyDomain;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/** Turns an {@link ItemEfficiency} into a stored row and back; the figures and rows are JSON. */
public final class ItemEfficiencyJson {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private record Rows(List<String> flags, List<ItemEfficiency.Detail> rows) {}

  private ItemEfficiencyJson() {}

  public static ItemEfficiencyDomain toRow(long matchId, ItemEfficiency e, int version) {
    return ItemEfficiencyDomain.builder()
        .matchId(matchId)
        .playerSlot(e.slot())
        .entryKey(e.entryKey())
        .itemKey(e.itemKey())
        .kind(e.kind())
        .purchaseSeconds(e.purchaseSeconds())
        .useCount(e.count())
        .gain(e.gain())
        .cost(e.cost())
        .ratio(e.ratio())
        .paybackSeconds(e.paybackSeconds())
        .figures(write(e.figures()))
        .details(write(new Rows(e.flags(), e.rows())))
        .analysisVersion(version)
        .build();
  }

  public static ItemEfficiency fromRow(ItemEfficiencyDomain row) {
    Rows stored =
        row.getDetails() == null ? null : read(row.getDetails(), new TypeReference<Rows>() {});
    Rows rows =
        new Rows(
            stored == null || stored.flags() == null ? List.of() : stored.flags(),
            stored == null || stored.rows() == null ? List.of() : stored.rows());
    return new ItemEfficiency(
        row.getPlayerSlot(),
        row.getEntryKey(),
        row.getItemKey(),
        row.getKind(),
        row.getPurchaseSeconds(),
        row.getUseCount(),
        row.getGain(),
        row.getCost(),
        row.getRatio(),
        row.getPaybackSeconds(),
        row.getFigures() == null
            ? Map.of()
            : read(row.getFigures(), new TypeReference<Map<String, Double>>() {}),
        rows.flags(),
        rows.rows());
  }

  private static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot store an item efficiency row", e);
    }
  }

  private static <T> T read(String json, TypeReference<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot read a stored item efficiency row", e);
    }
  }
}
