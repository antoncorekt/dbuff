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
  private static final String TRACKED = "Tracked players";

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

  /** Counts cover every missed fight, the lines only those within the cap. */
  private record Found(
      List<String> lines, int missed, int missedInLost, int unused, String subject) {}

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
    boolean several = found.subject().equals(TRACKED);
    List<String> parts = new ArrayList<>();
    if (found.missed() > 0) {
      parts.add(
          "%smissed %s%s"
              .formatted(
                  several ? TRACKED + " " : "",
                  SectionViews.count(found.missed(), "big fight"),
                  found.missedInLost() > 0 ? " (%d lost)".formatted(found.missedInLost()) : ""));
    }
    if (found.unused() > 0) {
      parts.add(
          "%sleft a ready ability unused in several fights"
              .formatted(several ? SectionViews.count(found.unused(), "player") + " " : ""));
    }
    String text = String.join(several ? "; " : " and ", parts);
    boolean problem = found.missedInLost() > 0 || found.unused() > 0;
    return SectionViews.titled(
        found.lines(),
        new Verdict(
            several ? text : found.subject() + " " + text, problem ? Accent.LOSS : Accent.NEUTRAL));
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
    int missedInLost = 0;
    int unused = 0;
    for (Map.Entry<Long, String> player : input.namesBySlot().entrySet()) {
      long slot = player.getKey();
      List<FightPlayerDomain> absent =
          parts.stream()
              .filter(p -> p.getPlayerSlot() == slot && p.getAbsenceReason() != null)
              .filter(
                  p -> fights.containsKey(p.getFightIndex()) && big(fights.get(p.getFightIndex())))
              .sorted(Comparator.comparing(p -> fights.get(p.getFightIndex()).getStartSeconds()))
              .toList();
      absent.stream()
          .limit(input.maxFindings())
          .map(p -> absenceLine(player.getValue(), slot, p, fights.get(p.getFightIndex())))
          .forEach(lines::add);
      missed += absent.size();
      missedInLost +=
          (int)
              absent.stream()
                  .filter(p -> "lost".equals(result(fights.get(p.getFightIndex()), slot)))
                  .count();
      Optional<String> ability = unusedAbilityLine(player.getValue(), slot, uses);
      ability.ifPresent(lines::add);
      unused += ability.isPresent() ? 1 : 0;
    }
    if (lines.isEmpty()) {
      return new Found(List.of(), 0, 0, 0, TRACKED);
    }
    lines.addFirst("**Fights**");
    String subject =
        input.namesBySlot().size() == 1 ? input.namesBySlot().values().iterator().next() : TRACKED;
    return new Found(lines, missed, missedInLost, unused, subject);
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
    String result = result(fight, slot);
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

  private static String result(FightDomain fight, long slot) {
    return fight.getWinner() == null || fight.getWinner().equals("DRAW")
        ? "drawn"
        : fight.getWinner().equals(slot >= FIRST_DIRE_SLOT ? "DIRE" : "RADIANT") ? "won" : "lost";
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
