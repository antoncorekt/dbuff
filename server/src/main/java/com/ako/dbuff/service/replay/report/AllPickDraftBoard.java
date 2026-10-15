package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DraftEventDomain;
import com.ako.dbuff.dao.repo.DraftEventRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The All Pick draft from OpenDota's picks and bans: the hero picks in draft order and the bans it
 * lists. OpenDota gives no times, so none are shown.
 */
@Component
@RequiredArgsConstructor
public class AllPickDraftBoard implements DraftBoard {
  private static final String FOOTNOTE =
      "Numbers are the order of the draft as OpenDota lists it, which gives no times. A hero shown"
          + " as not played was picked and then given up; a randomed hero is not listed.";

  private final DraftEventRepo eventRepo;
  private final PlayerGameStatisticRepo statRepo;
  private final ReportHeroNames heroNames;

  @Override
  public long gameModeId() {
    return AllPickProfile.MODE_ID;
  }

  @Override
  public Optional<SectionView> view(ReplayReportInput input) {
    List<DraftEventDomain> events = eventRepo.findByMatchIdOrderByDraftOrder(input.matchId());
    if (events.isEmpty()) {
      return Optional.empty();
    }
    List<PlayerGameStatisticRepo.HeroSlot> players = statRepo.findHeroSlots(input.matchId());
    Map<Long, Long> slotByHero = new HashMap<>();
    players.stream()
        .filter(p -> p.getHeroId() != null && p.getPlayerSlot() != null)
        .forEach(p -> slotByHero.put(p.getHeroId(), p.getPlayerSlot()));
    boolean heroesKnown =
        !players.isEmpty()
            && players.stream().allMatch(p -> p.getHeroId() != null && p.getPlayerSlot() != null);
    List<String> picks = new ArrayList<>();
    List<String> bans = new ArrayList<>();
    for (DraftEventDomain e : events) {
      if (e.getPick() == null || e.getHeroId() == null) {
        continue;
      }
      if (e.getPick()) {
        Long slot = slotByHero.get(e.getHeroId());
        picks.add(pickRow(e, slot, input.namesBySlot(), heroesKnown && slot == null));
      } else {
        bans.add(number(e) + heroNames.heroName(e.getHeroId()));
      }
    }
    if (picks.isEmpty() && bans.isEmpty()) {
      return Optional.empty();
    }
    List<String> cards = new ArrayList<>();
    cards.addAll(BoardRows.bullets("**Hero picks**", picks));
    cards.addAll(BoardRows.bullets("**Bans**", bans));
    return Optional.of(new SectionView("Draft board", null, Accent.INFO, cards, FOOTNOTE));
  }

  private String pickRow(
      DraftEventDomain pick, Long slot, Map<Long, String> names, boolean notPlayed) {
    String team =
        slot != null ? Prettify.team(TeamSide.ofSlot(slot).name()) : eventSide(pick.getTeam());
    String picker = slot == null ? null : names.get(slot);
    return String.format(
        "%s%s, picked by %s%s",
        number(pick),
        heroNames.heroName(pick.getHeroId()),
        picker == null ? team : picker + " (" + team + ")",
        notPlayed ? ", not played" : "");
  }

  private static String eventSide(Integer team) {
    return Integer.valueOf(0).equals(team)
        ? "Radiant"
        : Integer.valueOf(1).equals(team) ? "Dire" : "an unknown side";
  }

  private static String number(DraftEventDomain event) {
    return event.getDraftOrder() == null ? "" : (event.getDraftOrder() + 1) + ". ";
  }
}
