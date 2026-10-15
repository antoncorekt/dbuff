package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DraftEventDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.DraftEventRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
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
    Map<Long, Long> slotByHero = new HashMap<>();
    for (PlayerMatchStatisticDomain s : statRepo.findAllByMatchId(input.matchId())) {
      if (s.getHeroId() != null && s.getPlayerSlot() != null) {
        slotByHero.put(s.getHeroId(), s.getPlayerSlot());
      }
    }
    List<String> picks = new ArrayList<>();
    List<String> bans = new ArrayList<>();
    for (DraftEventDomain e : events) {
      if (Boolean.TRUE.equals(e.getPick())) {
        picks.add(pickRow(e, slotByHero.get(e.getHeroId()), input.namesBySlot()));
      } else {
        bans.add(number(e) + heroNames.heroName(e.getHeroId()));
      }
    }
    List<String> cards = new ArrayList<>();
    cards.addAll(BoardRows.bullets("**Hero picks**", picks));
    cards.addAll(BoardRows.bullets("**Bans**", bans));
    return Optional.of(new SectionView("Draft board", null, Accent.INFO, cards, FOOTNOTE));
  }

  private String pickRow(DraftEventDomain pick, Long slot, Map<Long, String> names) {
    String team = pick.getTeam() != null && pick.getTeam() == 1 ? "Dire" : "Radiant";
    String picker = slot == null ? null : names.get(slot);
    return String.format(
        "%s%s, picked by %s%s",
        number(pick),
        heroNames.heroName(pick.getHeroId()),
        picker == null ? team : picker + " (" + team + ")",
        slot == null ? ", not played" : "");
  }

  private static String number(DraftEventDomain event) {
    return event.getDraftOrder() == null ? "" : (event.getDraftOrder() + 1) + ". ";
  }
}
