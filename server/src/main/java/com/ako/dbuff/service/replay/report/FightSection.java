package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.service.replay.report.SectionViews.Verdict;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private final AbilityLabels labels;

  @Override
  public String name() {
    return "fights";
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }

  @Override
  public int order() {
    return 40;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.FULL;
  }

  private record Found(List<String> lines, int missed, int unused) {}

  @Override
  public List<String> lines(ReplayReportInput input) {
    return found(input).lines();
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    Found found = found(input);
    if (found.lines().isEmpty()) {
      return Optional.empty();
    }
    List<String> parts = new ArrayList<>();
    if (found.missed() > 0) {
      parts.add(
          "Tracked players missed %s".formatted(SectionViews.count(found.missed(), "big fight")));
    }
    if (found.unused() > 0) {
      parts.add(
          "%s left a ready ability unused in several fights"
              .formatted(SectionViews.count(found.unused(), "player")));
    }
    return SectionViews.titled(found.lines(), new Verdict(String.join("; ", parts), Accent.LOSS));
  }

  private Found found(ReplayReportInput input) {
    Map<Integer, FightDomain> fights =
        fightRepo.findByMatchId(input.matchId()).stream()
            .collect(
                Collectors.toMap(FightDomain::getFightIndex, Function.identity(), (a, b) -> a));
    List<FightPlayerDomain> parts = fightPlayerRepo.findByMatchId(input.matchId());
    List<FightAbilityUseDomain> uses = abilityRepo.findByMatchId(input.matchId());
    List<String> lines = new ArrayList<>();
    int missed = 0;
    int unused = 0;
    for (Map.Entry<Long, String> player : input.namesBySlot().entrySet()) {
      long slot = player.getKey();
      List<String> absences =
          parts.stream()
              .filter(p -> p.getPlayerSlot() == slot && p.getAbsenceReason() != null)
              .filter(
                  p -> fights.containsKey(p.getFightIndex()) && big(fights.get(p.getFightIndex())))
              .sorted(Comparator.comparing(p -> fights.get(p.getFightIndex()).getStartSeconds()))
              .limit(input.maxFindings())
              .map(p -> absenceLine(player.getValue(), slot, p, fights.get(p.getFightIndex())))
              .toList();
      lines.addAll(absences);
      missed += absences.size();
      Optional<String> ability = unusedAbilityLine(player.getValue(), slot, uses);
      ability.ifPresent(lines::add);
      unused += ability.isPresent() ? 1 : 0;
    }
    if (lines.isEmpty()) {
      return new Found(List.of(), 0, 0);
    }
    lines.addFirst("**Fights**");
    return new Found(lines, missed, unused);
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
    return "%s %s (%s, deaths %d to %d): %s was %s, %s."
        .formatted(
            GameTime.clock(fight.getStartSeconds()),
            "TEAMFIGHT".equals(fight.getType()) ? "teamfight" : "fight",
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
  private Optional<String> unusedAbilityLine(
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
        .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
        .map(
            e ->
                "%s had %s ready and did not cast it in %d of %d fights."
                    .formatted(
                        name,
                        labels.label(e.getKey()),
                        e.getValue(),
                        byAbility.get(e.getKey()).size()));
  }
}
