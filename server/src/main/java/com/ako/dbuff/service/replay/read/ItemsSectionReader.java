package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.ItemEfficiencyDomain;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(70)
@RequiredArgsConstructor
class ItemsSectionReader implements AnalysisSectionReader {
  private final ItemEfficiencyRepo efficiencyRepo;

  public record ItemEfficiency(
      Long slot,
      String entryKey,
      String itemKey,
      String kind,
      Double purchaseSeconds,
      Integer useCount,
      Double gain,
      Long cost,
      Double ratio,
      Double paybackSeconds,
      String figures) {}

  @Override
  public String name() {
    return "items";
  }

  @Override
  public List<ItemEfficiency> read(long matchId, RunStatus run) {
    return efficiencyRepo.findByMatchId(matchId).stream()
        .sorted(
            RowOrder.nullsLast(ItemEfficiencyDomain::getPlayerSlot)
                .thenComparing(RowOrder.nullsLast(ItemEfficiencyDomain::getPurchaseSeconds))
                .thenComparing(RowOrder.nullsLast(ItemEfficiencyDomain::getEntryKey))
                .thenComparing(RowOrder.nullsLast(ItemEfficiencyDomain::getId)))
        .map(
            i ->
                new ItemEfficiency(
                    i.getPlayerSlot(),
                    i.getEntryKey(),
                    i.getItemKey(),
                    i.getKind(),
                    i.getPurchaseSeconds(),
                    i.getUseCount(),
                    i.getGain(),
                    i.getCost(),
                    i.getRatio(),
                    i.getPaybackSeconds(),
                    i.getFigures()))
        .toList();
  }
}
