package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.AbilityDraftPoolDomain;
import com.ako.dbuff.dao.model.HeroDraftPickDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.dao.repo.HeroDraftPickRepo;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
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
public class DraftBoardSection implements ReplayReportSection {
  private static final String FOOTNOTE =
      "Turns count skill and hero picks together; times are on the game clock, before the horn.";

  private final HeroDraftPickRepo heroRepo;
  private final AbilityDraftPickRepo skillRepo;
  private final AbilityDraftPoolRepo poolRepo;
  private final AbilityDraftProfile profile;
  private final AbilityLabels labels;
  private final ReportHeroNames heroNames;
  private final PlainViewRenderer plain;

  @Override
  public String name() {
    return "draft-board";
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return mode.gameModeId() == profile.gameModeId();
  }

  @Override
  public int order() {
    return 11;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    return view(input).stream()
        .flatMap(v -> plain.lines(v).stream())
        .flatMap(line -> line.lines())
        .toList();
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
    cards.add(board.heroPicks());
    if (!skills.isEmpty()) {
      cards.add(board.skillPicks(skills));
    }
    board.leftOver(pool).ifPresent(cards::add);
    return Optional.of(new SectionView("Draft board", null, Accent.INFO, cards, FOOTNOTE));
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

    String heroPicks() {
      Stream<String> rows =
          heroes.stream()
              .filter(h -> h.getPlayerSlot() != null)
              .sorted(
                  Comparator.comparing(
                      HeroDraftPickDomain::getPickTick,
                      Comparator.nullsLast(Comparator.naturalOrder())))
              .map(this::heroRow);
      return Stream.concat(Stream.of("**Hero picks**"), rows).collect(Collectors.joining("\n"));
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

    String skillPicks(List<AbilityDraftPickDomain> skills) {
      Map<Long, List<AbilityDraftPickDomain>> bySlot =
          skills.stream()
              .filter(s -> s.getPlayerSlot() != null && s.getAbilityName() != null)
              .collect(Collectors.groupingBy(AbilityDraftPickDomain::getPlayerSlot));
      TeamSide yours = TeamSide.commonSide(input.focusSlots());
      Comparator<Long> order =
          Comparator.<Long, Boolean>comparing(
                  slot -> yours == null || TeamSide.ofSlot(slot) != yours)
              .thenComparing(Comparator.naturalOrder());
      Stream<String> rows =
          bySlot.keySet().stream().sorted(order).map(slot -> skillRow(slot, bySlot.get(slot)));
      return Stream.concat(Stream.of("**Skill picks**"), rows).collect(Collectors.joining("\n"));
    }

    private String skillRow(long slot, List<AbilityDraftPickDomain> picks) {
      String picker = input.namesBySlot().get(slot);
      String hero = heroBySlot.get(slot);
      String who =
          picker != null
              ? picker + (hero == null ? "" : " (" + hero + ")")
              : hero == null ? team(slot) + " player" : hero;
      String chosen =
          picks.stream()
              .sorted(Comparator.comparingInt(AbilityDraftPickDomain::getPickOrder))
              .map(
                  p ->
                      labels.label(p.getAbilityName())
                          + " ("
                          + (p.getTurnNumber() == null ? p.getPickOrder() : p.getTurnNumber())
                          + ")")
              .collect(Collectors.joining(", "));
      return who + ": " + chosen;
    }

    Optional<String> leftOver(List<AbilityDraftPoolDomain> pool) {
      List<String> leftHeroes =
          heroes.stream()
              .filter(h -> h.getPlayerSlot() == null)
              .map(h -> heroNames.heroName(h.getHeroId()))
              .toList();
      List<String> leftSkills =
          pool.stream()
              .filter(p -> p.getPickedBySlot() == null)
              .sorted(Comparator.comparing(AbilityDraftPoolDomain::getPoolIndex))
              .map(
                  p ->
                      p.getAbilityName() == null
                          ? "Skill " + p.getAbilityId()
                          : labels.label(p.getAbilityName()))
              .toList();
      List<String> rows = new ArrayList<>();
      if (!leftHeroes.isEmpty()) {
        rows.add("Heroes: " + String.join(", ", leftHeroes));
      }
      if (!leftSkills.isEmpty()) {
        rows.add("Skills: " + String.join(", ", leftSkills));
      }
      return rows.isEmpty()
          ? Optional.empty()
          : Optional.of("**Left over**\n" + String.join("\n", rows));
    }

    private String team(long slot) {
      return Prettify.team(TeamSide.ofSlot(slot).name());
    }
  }
}
