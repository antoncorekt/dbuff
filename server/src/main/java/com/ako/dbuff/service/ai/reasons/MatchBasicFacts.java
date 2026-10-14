package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.GameTime;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The facts that come from the match itself and not from a report section: the result, the heroes
 * of each team, the end-of-game numbers of each hero and the role each filled.
 */
@Component
@RequiredArgsConstructor
class MatchBasicFacts {
  private static final String[] ROLES = {
    "carry", "mid", "offlane", "roaming or jungle support", "hard support"
  };

  private final PlayerGameStatisticRepo statRepo;
  private final PlayerRoleRepo roleRepo;
  private final ReportHeroNames heroNames;

  List<Fact> build(ReplayReportInput input) {
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    Map<Long, String> heroes = heroNames.bySlot(input.matchId());
    List<PlayerMatchStatisticDomain> stats =
        statRepo.findAllByMatchId(input.matchId()).stream()
            .filter(s -> s.getPlayerSlot() != null && heroes.containsKey(s.getPlayerSlot()))
            .sorted(Comparator.comparing(PlayerMatchStatisticDomain::getPlayerSlot))
            .toList();
    List<Fact> facts = new ArrayList<>();
    result(input.match(), yours).ifPresent(facts::add);
    for (TeamSide side : TeamSide.values()) {
      draft(side, yours, heroes, stats).ifPresent(facts::add);
    }
    stats.forEach(s -> facts.add(stat(s, heroes.get(s.getPlayerSlot()), yours)));
    for (PlayerRoleDomain role : roleRepo.findByMatchId(input.matchId())) {
      role(role, heroes, yours).ifPresent(facts::add);
    }
    return facts;
  }

  private static Optional<Fact> result(MatchDomain match, TeamSide yours) {
    if (match == null || match.getRadiantWin() == null) {
      return Optional.empty();
    }
    TeamSide winner = match.getRadiantWin() ? TeamSide.RADIANT : TeamSide.DIRE;
    StringBuilder text = new StringBuilder(TeamLabels.name(winner)).append(" won");
    if (match.getDuration() != null) {
      text.append(" after ").append(GameTime.clock(match.getDuration()));
    }
    text.append('.');
    if (match.getRadiantScore() != null && match.getDireScore() != null) {
      text.append(
          String.format(
              Locale.ROOT,
              " Final kills: Radiant %d, Dire %d.",
              match.getRadiantScore(),
              match.getDireScore()));
    }
    if (yours != null) {
      text.append(" Your team is ")
          .append(TeamLabels.name(yours))
          .append(" and it ")
          .append(yours == winner ? "won" : "lost")
          .append('.');
    }
    return Optional.of(new Fact(FactKind.RESULT.id(""), FactKind.RESULT, text.toString()));
  }

  private static Optional<Fact> draft(
      TeamSide side,
      TeamSide yours,
      Map<Long, String> heroes,
      List<PlayerMatchStatisticDomain> stats) {
    String names =
        stats.stream()
            .filter(s -> TeamSide.ofSlot(s.getPlayerSlot()) == side)
            .map(s -> heroes.get(s.getPlayerSlot()))
            .collect(Collectors.joining(", "));
    if (names.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new Fact(
            FactKind.DRAFT.id(side.name()),
            FactKind.DRAFT,
            "Heroes of " + TeamLabels.of(side, yours) + ": " + names + "."));
  }

  private static Fact stat(PlayerMatchStatisticDomain s, String hero, TeamSide yours) {
    TeamSide side = TeamSide.ofSlot(s.getPlayerSlot());
    List<String> parts = new ArrayList<>();
    if (s.getKills() != null && s.getDeaths() != null && s.getAssists() != null) {
      parts.add(s.getKills() + "/" + s.getDeaths() + "/" + s.getAssists() + " K/D/A");
    }
    if (s.getGoldPerMin() != null) {
      parts.add(s.getGoldPerMin() + " GPM");
    }
    if (s.getXpPerMin() != null) {
      parts.add(s.getXpPerMin() + " XPM");
    }
    if (s.getNetWorth() != null) {
      parts.add(String.format(Locale.ROOT, "net worth %.1fk", s.getNetWorth() / 1000.0));
    }
    return new Fact(
        FactKind.STATS.id(idPart(hero)),
        FactKind.STATS,
        hero + ", " + TeamLabels.of(side, yours) + ": " + String.join(", ", parts) + ".");
  }

  private static Optional<Fact> role(
      PlayerRoleDomain role, Map<Long, String> heroes, TeamSide yours) {
    String hero = role.getPlayerSlot() == null ? null : heroes.get(role.getPlayerSlot());
    Integer number = role.getRole();
    if (hero == null || number == null || number < 1 || number > ROLES.length) {
      return Optional.empty();
    }
    TeamSide side = TeamSide.ofSlot(role.getPlayerSlot());
    return Optional.of(
        new Fact(
            FactKind.ROLE.id(idPart(hero)),
            FactKind.ROLE,
            hero
                + ", "
                + TeamLabels.of(side, yours)
                + ", played position "
                + number
                + " ("
                + ROLES[number - 1]
                + ")."));
  }

  private static String idPart(String hero) {
    return hero.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
  }
}
