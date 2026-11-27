package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.dao.model.MissingDefenseItemDomain;
import com.ako.dbuff.dao.model.TeamDefenseItemDomain;
import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import com.ako.dbuff.dao.repo.DefenseStatRepository;
import com.ako.dbuff.dao.repo.DefenseStatRepository.ItemSituation;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MissingDefenseItemRepo;
import com.ako.dbuff.dao.repo.TeamDefenseItemRepo;
import com.ako.dbuff.dao.repo.TeamDefensePointRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * At minutes 20 and 30, reports a team that took mostly magical or physical damage and had no
 * defensive item of that kind yet, with how stored games of the same mode went with and without
 * one.
 */
@Slf4j
@Order(43)
@Component
@RequiredArgsConstructor
public class MissingDefenseItemAnalyzer implements ReplayAnalyzer {
  private static final String MAGIC = "MAGIC";
  private static final String PHYSICAL = "PHYSICAL";
  private static final int SECONDS_PER_MINUTE = 60;
  private static final Set<String> CHECKPOINTS = Set.of("M20", "M30");

  private final TeamDefensePointRepo pointRepo;
  private final TeamDefenseItemRepo itemRepo;
  private final MissingDefenseItemRepo missingRepo;
  private final DefenseStatRepository stats;
  private final StoredMatchModes modes;
  private final MatchRepo matchRepo;
  private final MissingDefenseProperties properties;
  private final MissingDefenseWording wording;

  @Override
  public String name() {
    return "missing-defense-items";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("team-defense", "defense-items");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    Long modeId = modes.activeFor(matchId).map(m -> m.gameModeId()).orElse(null);
    Long start = matchRepo.findById(matchId).map(m -> m.getStartTime()).orElse(null);
    if (modeId == null || start == null) {
      return;
    }
    long from =
        Instant.ofEpochSecond(start)
            .atZone(ZoneOffset.UTC)
            .minusMonths(properties.getWindowMonths())
            .toEpochSecond();
    List<TeamDefenseItemDomain> items = itemRepo.findByMatchId(matchId);
    List<MissingDefenseItemDomain> rows = new ArrayList<>();
    for (TeamDefensePointDomain point : pointRepo.findByMatchId(matchId)) {
      if (point.getCheckpoint() == null || !CHECKPOINTS.contains(point.getCheckpoint())) {
        continue;
      }
      addIfMissing(rows, point, items, MAGIC, point.getEnemyMagShare(), properties.getMagicShare());
      addIfMissing(
          rows, point, items, PHYSICAL, point.getEnemyPhysShare(), properties.getPhysicalShare());
    }
    for (MissingDefenseItemDomain row : rows) {
      ItemSituation history =
          stats.itemSituation(
              modeId.intValue(),
              row.getCheckpoint(),
              row.getKind(),
              MAGIC.equals(row.getKind())
                  ? properties.getMagicShare()
                  : properties.getPhysicalShare(),
              from,
              start,
              matchId);
      row.setWithGames(history.withGames());
      row.setWithWins(history.withWins());
      row.setWithoutGames(history.withoutGames());
      row.setWithoutWins(history.withoutWins());
      row.setEvidence(fit(wording.line(row)));
    }
    missingRepo.saveAll(rows);
    log.info("Match {} has {} missing defensive items", matchId, rows.size());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    missingRepo.deleteByMatchId(matchId);
  }

  private void addIfMissing(
      List<MissingDefenseItemDomain> rows,
      TeamDefensePointDomain point,
      List<TeamDefenseItemDomain> items,
      String kind,
      Double share,
      double bar) {
    if (share == null || share < bar || point.getMinute() == null) {
      return;
    }
    boolean held =
        items.stream()
            .anyMatch(
                i ->
                    point.getTeam().equals(i.getTeam())
                        && kind.equals(i.getKind())
                        && i.getAtSecond() <= point.getMinute() * SECONDS_PER_MINUTE);
    if (held) {
      return;
    }
    rows.add(
        MissingDefenseItemDomain.builder()
            .matchId(point.getMatchId())
            .team(point.getTeam())
            .checkpoint(point.getCheckpoint())
            .kind(kind)
            .minute(point.getMinute())
            .enemyShare(share)
            .analysisVersion(version())
            .build());
  }

  private static String fit(String text) {
    int max = MissingDefenseItemDomain.EVIDENCE_LENGTH;
    return text.length() <= max ? text : text.substring(0, max);
  }
}
