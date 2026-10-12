package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Where the channel's tracked players were when a big fight began and could not or did not join,
 * and which abilities they had ready and left unused.
 */
@Component
@RequiredArgsConstructor
public class FightSection implements ReplayReportSection {
  private static final long FIRST_DIRE_SLOT = 128;
  private static final int BIG_FIGHT_DEATHS = 3;
  private static final int MIN_FIGHTS_FOR_ABILITY_LINE = 2;

  private final FightRepo fightRepo;
  private final FightPlayerRepo fightPlayerRepo;
  private final FightAbilityUseRepo abilityRepo;

  @Override
  public int order() {
    return 40;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.FULL;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    Map<Integer, FightDomain> fights =
        fightRepo.findByMatchId(input.matchId()).stream()
            .collect(
                Collectors.toMap(FightDomain::getFightIndex, Function.identity(), (a, b) -> a));
    List<FightPlayerDomain> parts = fightPlayerRepo.findByMatchId(input.matchId());
    List<FightAbilityUseDomain> uses = abilityRepo.findByMatchId(input.matchId());
    List<String> lines = new ArrayList<>();
    for (Map.Entry<Long, String> player : input.namesBySlot().entrySet()) {
      long slot = player.getKey();
      parts.stream()
          .filter(p -> p.getPlayerSlot() == slot && p.getAbsenceReason() != null)
          .filter(p -> fights.containsKey(p.getFightIndex()) && big(fights.get(p.getFightIndex())))
          .sorted(Comparator.comparing(p -> fights.get(p.getFightIndex()).getStartSeconds()))
          .limit(input.maxFindings())
          .map(p -> absenceLine(player.getValue(), slot, p, fights.get(p.getFightIndex())))
          .forEach(lines::add);
      unusedAbilityLine(player.getValue(), slot, uses).ifPresent(lines::add);
    }
    if (lines.isEmpty()) {
      return List.of();
    }
    lines.addFirst("**Fights**");
    return lines;
  }

  private static boolean big(FightDomain fight) {
    return "TEAMFIGHT".equals(fight.getType())
        || fight.getRadiantDeaths() + fight.getDireDeaths() >= BIG_FIGHT_DEATHS;
  }

  private static String absenceLine(
      String name, long slot, FightPlayerDomain part, FightDomain fight) {
    boolean dire = slot >= FIRST_DIRE_SLOT;
    int own = dire ? fight.getDireDeaths() : fight.getRadiantDeaths();
    int other = dire ? fight.getRadiantDeaths() : fight.getDireDeaths();
    String result =
        fight.getWinner() == null || fight.getWinner().equals("DRAW")
            ? "drawn"
            : fight.getWinner().equals(dire ? "DIRE" : "RADIANT") ? "won" : "lost";
    return "%s teamfight (%s, deaths %d to %d): %s was %s, %s."
        .formatted(
            GameTime.clock(fight.getStartSeconds()),
            result,
            own,
            other,
            name,
            where(part.getPresence()),
            why(part));
  }

  private static String where(String presence) {
    return switch (presence) {
      case "ABSENT_FARMING" -> "farming away from it";
      case "ABSENT_PUSHING" -> "pushing elsewhere";
      case "ABSENT_BASE" -> "in the base";
      default -> "far from it";
    };
  }

  private static String why(FightPlayerDomain part) {
    return switch (part.getAbsenceReason()) {
      case "NO_TP" -> "with no teleport";
      case "TP_ON_COOLDOWN" ->
          "his teleport had " + part.getTeleportCooldownLeft() + " s of cooldown left";
      default -> "with a teleport ready";
    };
  }

  /** "Ravage ready, not cast in 3 of 5 fights", for the worst such ability. */
  private static java.util.Optional<String> unusedAbilityLine(
      String name, long slot, List<FightAbilityUseDomain> uses) {
    Map<String, List<FightAbilityUseDomain>> byAbility =
        uses.stream()
            .filter(u -> u.getPlayerSlot() == slot)
            .collect(Collectors.groupingBy(FightAbilityUseDomain::getAbilityName));
    return byAbility.entrySet().stream()
        .map(
            e ->
                Map.entry(
                    e.getKey(),
                    e.getValue().stream()
                        .filter(u -> "READY_NOT_CAST".equals(u.getNonCastReason()))
                        .count()))
        .filter(e -> e.getValue() >= MIN_FIGHTS_FOR_ABILITY_LINE)
        .max(Map.Entry.comparingByValue())
        .map(
            e ->
                "%s had %s ready and did not cast it in %d of %d fights."
                    .formatted(
                        name,
                        Prettify.ability(e.getKey()),
                        e.getValue(),
                        byAbility.get(e.getKey()).size()));
  }
}
