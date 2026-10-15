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
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
      "Each player's skills and hero in the order they were picked; the number is the turn of the"
          + " whole draft, skills and heroes counted together.";
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
    cards.addAll(BoardRows.bullets("**Picks in draft order**", board.picks(skills)));
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

  /** One pick of a player, a skill or the hero, with what orders it in the draft. */
  private record Pick(String label, Integer turn, Integer tick, boolean hero, int order) {
    /** Turn numbers first; picks without one follow by tick, a skill before a hero on a tie. */
    static final Comparator<Pick> DRAFT_ORDER =
        Comparator.comparing((Pick p) -> p.turn == null)
            .thenComparing(Pick::turn, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Pick::tick, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Pick::hero)
            .thenComparingInt(Pick::order);
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

    List<String> picks(List<AbilityDraftPickDomain> skills) {
      Map<Long, List<Pick>> bySlot = new TreeMap<>();
      skills.stream()
          .filter(s -> s.getPlayerSlot() != null)
          .forEach(
              s ->
                  bySlot
                      .computeIfAbsent(s.getPlayerSlot(), k -> new ArrayList<>())
                      .add(
                          new Pick(
                              skillName(s.getAbilityName(), s.getAbilityId()),
                              s.getTurnNumber(),
                              s.getPickTick(),
                              false,
                              s.getPickOrder())));
      heroes.stream()
          .filter(h -> h.getPlayerSlot() != null)
          .forEach(
              h ->
                  bySlot
                      .computeIfAbsent(h.getPlayerSlot(), k -> new ArrayList<>())
                      .add(
                          new Pick(
                              "hero " + heroNames.heroName(h.getHeroId()),
                              h.getTurnNumber(),
                              h.getPickTick(),
                              true,
                              0)));
      TeamSide yours = TeamSide.commonSide(input.focusSlots());
      Comparator<Long> order =
          Comparator.<Long, Boolean>comparing(
                  slot -> yours == null || TeamSide.ofSlot(slot) != yours)
              .thenComparing(Comparator.naturalOrder());
      return bySlot.keySet().stream()
          .sorted(order)
          .flatMap(slot -> row(slot, bySlot.get(slot)).stream())
          .toList();
    }

    private List<String> row(long slot, List<Pick> picks) {
      String picker = input.namesBySlot().get(slot);
      String hero = heroBySlot.get(slot);
      String who =
          picker != null
              ? picker + (hero == null ? "" : " (" + hero + ")")
              : hero == null ? team(slot) + " player" : hero;
      List<String> chosen =
          picks.stream()
              .sorted(Pick.DRAFT_ORDER)
              .map(p -> p.label() + (p.turn() == null ? "" : " (" + p.turn() + ")"))
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
