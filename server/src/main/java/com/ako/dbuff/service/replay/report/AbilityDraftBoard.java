package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.AbilityDraftPoolDomain;
import com.ako.dbuff.dao.model.HeroDraftPickDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.dao.repo.HeroDraftPickRepo;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The Ability Draft as one board: the hero picks in turn order, the skills each hero took with
 * their turns, and what nobody took. Turns count skills and heroes together, 1 to 50.
 */
@Component
@RequiredArgsConstructor
public class AbilityDraftBoard implements DraftBoard {
  /** A wrapped line stays well under the 2000 characters of a plain message. */
  private static final int ROW_LIMIT = 1500;

  private static final String FOOTNOTE =
      "Turns count skill and hero picks together; times are on the game clock, before the horn.";
  private static final String ORDER_UNKNOWN = " Where no turn is shown, the order is not known.";

  private final HeroDraftPickRepo heroRepo;
  private final AbilityDraftPickRepo skillRepo;
  private final AbilityDraftPoolRepo poolRepo;
  private final AbilityDraftProfile profile;
  private final AbilityLabels labels;
  private final ReportHeroNames heroNames;

  @Override
  public long gameModeId() {
    return profile.gameModeId();
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    List<HeroDraftPickDomain> heroes = heroRepo.findByMatchId(input.matchId());
    if (heroes.isEmpty()) {
      return Optional.empty();
    }
    List<AbilityDraftPickDomain> skills = skillRepo.findByMatchId(input.matchId());
    List<AbilityDraftPoolDomain> pool = poolRepo.findByMatchId(input.matchId());
    Board board = new Board(input, heroes);
    List<String> cards = new ArrayList<>();
    cards.addAll(BoardRows.bullets("**Hero picks**", board.heroPicks()));
    if (!skills.isEmpty()) {
      cards.addAll(BoardRows.bullets("**Skill picks**", board.skillPicks(skills)));
    }
    cards.addAll(BoardRows.bullets("**Left over**", board.leftOver(pool)));
    boolean numbersMissing =
        heroes.stream().anyMatch(h -> h.getPlayerSlot() != null && h.getTurnNumber() == null)
            || skills.stream().anyMatch(s -> s.getTurnNumber() == null);
    return Optional.of(
        new SectionView(
            "Draft board",
            null,
            Accent.INFO,
            cards,
            numbersMissing ? FOOTNOTE + ORDER_UNKNOWN : FOOTNOTE));
  }

  /**
   * {@code lead} and the parts joined by commas, wrapped into lines of at most {@link #ROW_LIMIT}.
   */
  private static List<String> wrapped(String lead, List<String> parts) {
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder(lead);
    boolean empty = true;
    for (String part : parts) {
      if (!empty && line.length() + 2 + part.length() > ROW_LIMIT) {
        lines.add(line.append(',').toString());
        line = new StringBuilder(part);
      } else {
        line.append(empty ? "" : ", ").append(part);
      }
      empty = false;
    }
    lines.add(line.toString());
    return lines;
  }

  private final class Board {
    private final ReplayReportInput input;
    private final List<HeroDraftPickDomain> heroes;
    private final Map<Long, String> heroBySlot = new TreeMap<>();

    Board(ReplayReportInput input, List<HeroDraftPickDomain> heroes) {
      this.input = input;
      this.heroes = heroes;
      heroes.stream()
          .filter(h -> h.getPlayerSlot() != null)
          .forEach(h -> heroBySlot.put(h.getPlayerSlot(), heroNames.heroName(h.getHeroId())));
    }

    List<String> heroPicks() {
      Stream<String> rows =
          heroes.stream()
              .filter(h -> h.getPlayerSlot() != null)
              .sorted(
                  Comparator.comparing(
                      HeroDraftPickDomain::getPickTick,
                      Comparator.nullsLast(Comparator.naturalOrder())))
              .map(this::heroRow);
      return rows.toList();
    }

    private String heroRow(HeroDraftPickDomain pick) {
      long slot = pick.getPlayerSlot();
      String picker = input.namesBySlot().get(slot);
      String team = team(slot);
      return String.format(
          Locale.ROOT,
          "%s%s, picked by %s at %s",
          pick.getTurnNumber() == null ? "" : pick.getTurnNumber() + ". ",
          heroNames.heroName(pick.getHeroId()),
          picker == null ? team : picker + " (" + team + ")",
          pick.getClockSeconds() == null ? "?" : GameTime.clock(pick.getClockSeconds()));
    }

    List<String> skillPicks(List<AbilityDraftPickDomain> skills) {
      Map<Long, List<AbilityDraftPickDomain>> bySlot =
          skills.stream()
              .filter(s -> s.getPlayerSlot() != null)
              .collect(Collectors.groupingBy(AbilityDraftPickDomain::getPlayerSlot));
      TeamSide yours = TeamSide.commonSide(input.focusSlots());
      Comparator<Long> order =
          Comparator.<Long, Boolean>comparing(
                  slot -> yours == null || TeamSide.ofSlot(slot) != yours)
              .thenComparing(Comparator.naturalOrder());
      return bySlot.keySet().stream()
          .sorted(order)
          .flatMap(slot -> skillRow(slot, bySlot.get(slot)).stream())
          .toList();
    }

    private List<String> skillRow(long slot, List<AbilityDraftPickDomain> picks) {
      String picker = input.namesBySlot().get(slot);
      String hero = heroBySlot.get(slot);
      String who =
          picker != null
              ? picker + (hero == null ? "" : " (" + hero + ")")
              : hero == null ? team(slot) + " player" : hero;
      List<String> chosen =
          picks.stream()
              .sorted(Comparator.comparingInt(AbilityDraftPickDomain::getPickOrder))
              .map(
                  p ->
                      skillName(p.getAbilityName(), p.getAbilityId())
                          + (p.getTurnNumber() == null ? "" : " (" + p.getTurnNumber() + ")"))
              .toList();
      return wrapped(who + ": ", chosen);
    }

    List<String> leftOver(List<AbilityDraftPoolDomain> pool) {
      List<String> leftHeroes =
          heroes.stream()
              .filter(h -> h.getPlayerSlot() == null)
              .map(h -> heroNames.heroName(h.getHeroId()))
              .toList();
      List<String> leftSkills =
          pool.stream()
              .filter(p -> p.getPickedBySlot() == null)
              .sorted(Comparator.comparing(AbilityDraftPoolDomain::getPoolIndex))
              .map(p -> skillName(p.getAbilityName(), p.getAbilityId()))
              .toList();
      List<String> rows = new ArrayList<>();
      if (!leftHeroes.isEmpty()) {
        rows.addAll(wrapped("Heroes: ", leftHeroes));
      }
      if (!leftSkills.isEmpty()) {
        rows.addAll(wrapped("Skills: ", leftSkills));
      }
      return rows;
    }

    private String skillName(String abilityName, Long abilityId) {
      return abilityName == null ? "Skill " + abilityId : labels.decorated(abilityName);
    }

    private String team(long slot) {
      return Prettify.team(TeamSide.ofSlot(slot).name());
    }
  }
}
