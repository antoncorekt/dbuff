package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.model.TeamDefenseItemDomain;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.dao.repo.TeamDefenseItemRepo;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notes when each hero first holds a defensive item of the catalog, from the stored item changes. A
 * later gain counts again only when the hero had lost the item for more than a minute, so moving it
 * between inventory slots or backpack does not repeat it.
 */
@Slf4j
@Order(42)
@Component
@RequiredArgsConstructor
public class DefenseItemAnalyzer implements ReplayAnalyzer {
  static final double REBUY_GAP_SECONDS = 60;

  private final ReplayStateChangeRepo stateRepo;
  private final TeamDefenseItemRepo itemRepo;
  private final DefenseItemsFile catalog;

  @Override
  public String name() {
    return "defense-items";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "state-changes");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    Map<String, Double> lostAt = new HashMap<>();
    Map<String, Boolean> held = new HashMap<>();
    List<TeamDefenseItemDomain> rows = new ArrayList<>();
    for (ReplayStateChangeDomain c : stateRepo.findByMatchIdOrderByGameSeconds(matchId)) {
      DefenseItemKind kind = catalog.kindOfItemName(c.getName());
      StateChangeKind change = StateChangeKind.valueOf(c.getKind());
      if (kind == null
          || (change != StateChangeKind.ITEM_ADDED && change != StateChangeKind.ITEM_REMOVED)) {
        continue;
      }
      String id = c.getPlayerSlot() + "/" + c.getName();
      if (change == StateChangeKind.ITEM_REMOVED) {
        lostAt.put(id, c.getGameSeconds());
        continue;
      }
      Double lost = lostAt.remove(id);
      boolean first = held.put(id, true) == null;
      if (first || (lost != null && c.getGameSeconds() - lost > REBUY_GAP_SECONDS)) {
        rows.add(row(matchId, c, kind));
      }
    }
    itemRepo.saveAll(rows);
    log.info("Match {} has {} defensive item gains", matchId, rows.size());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    itemRepo.deleteByMatchId(matchId);
  }

  private TeamDefenseItemDomain row(long matchId, ReplayStateChangeDomain c, DefenseItemKind kind) {
    return TeamDefenseItemDomain.builder()
        .matchId(matchId)
        .team(PlayerSlots.radiant(c.getPlayerSlot()) ? "RADIANT" : "DIRE")
        .playerSlot(c.getPlayerSlot())
        .item(c.getName().substring("item_".length()))
        .kind(kind.name())
        .atSecond((int) Math.floor(c.getGameSeconds()))
        .analysisVersion(version())
        .build();
  }
}
