package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.dao.model.MissingDefenseItemDomain;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A missing defensive item row in plain words. */
@Component
@RequiredArgsConstructor
public class MissingDefenseWording {
  private final MissingDefenseProperties properties;

  public String line(MissingDefenseItemDomain row) {
    boolean magic = "MAGIC".equals(row.getKind());
    String head =
        "By %d:00 %s took %d%% %s damage and had no %s defence item (%s)."
            .formatted(
                row.getMinute(),
                team(row.getTeam()),
                Math.round(row.getEnemyShare() * 100),
                magic ? "magical" : "physical",
                magic ? "magic" : "physical",
                magic
                    ? "Pipe, Eternal Shroud, BKB..."
                    : "Assault Cuirass, Crimson Guard, Shiva's Guard...");
    if (row.getWithGames() < properties.getMinGames()
        || row.getWithoutGames() < properties.getMinGames()) {
      return head + " Not enough stored games to compare.";
    }
    return head
        + " In stored games: with one %d of %d won, without %d of %d."
            .formatted(
                row.getWithWins(), row.getWithGames(), row.getWithoutWins(), row.getWithoutGames());
  }

  private static String team(String team) {
    return team.charAt(0) + team.substring(1).toLowerCase(Locale.ROOT);
  }
}
