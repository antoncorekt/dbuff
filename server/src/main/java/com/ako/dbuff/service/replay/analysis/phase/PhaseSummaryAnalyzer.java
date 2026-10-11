package com.ako.dbuff.service.replay.analysis.phase;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.PlayerActivitySegmentDomain;
import com.ako.dbuff.dao.model.PlayerPhaseSummaryDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.dao.repo.PlayerPhaseSummaryRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerMinuteRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sums up each player's laning, mid and late game: where his time went, his kills, deaths and
 * assists, income, and how many of the fights of the phase he took part in.
 */
@Order(50)
@Component
@RequiredArgsConstructor
public class PhaseSummaryAnalyzer implements ReplayAnalyzer {
  private static final Set<String> JOINED = Set.of("ACTIVE", "PRESENT_PASSIVE");

  private final PlayerActivitySegmentRepo segmentRepo;
  private final ReplayPlayerMinuteRepo minuteRepo;
  private final PlayerTimelineRepo timelineRepo;
  private final FightRepo fightRepo;
  private final FightPlayerRepo fightPlayerRepo;
  private final PlayerPhaseSummaryRepo summaryRepo;
  private final ReplayAnalysisProperties properties;

  @Override
  public String name() {
    return "phase-summaries";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "tracks");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    List<PlayerActivitySegmentDomain> segments = segmentRepo.findByMatchId(matchId);
    int gameSeconds =
        segments.stream().mapToInt(PlayerActivitySegmentDomain::getEndSeconds).max().orElse(0);
    List<PhaseWindows.Window> windows =
        PhaseWindows.of(
            properties.getPhaseLaneEndMinutes(), properties.getPhaseMidEndMinutes(), gameSeconds);
    if (windows.isEmpty()) {
      return;
    }
    Map<Long, List<ActivityTime.Span>> spans =
        segments.stream()
            .collect(
                Collectors.groupingBy(
                    PlayerActivitySegmentDomain::getPlayerSlot,
                    Collectors.mapping(
                        s ->
                            new ActivityTime.Span(
                                s.getStartSeconds(),
                                s.getEndSeconds(),
                                Activity.valueOf(s.getActivity())),
                        Collectors.toList())));
    Map<Long, List<ReplayPlayerMinuteDomain>> minutes =
        minuteRepo.findByMatchId(matchId).stream()
            .collect(Collectors.groupingBy(ReplayPlayerMinuteDomain::getPlayerSlot));
    Map<Long, List<PlayerTimelineDomain>> timelines =
        timelineRepo.findByMatchId(matchId).stream()
            .collect(Collectors.groupingBy(PlayerTimelineDomain::getPlayerSlot));
    Map<Integer, FightDomain> fights =
        fightRepo.findByMatchId(matchId).stream()
            .collect(Collectors.toMap(FightDomain::getFightIndex, Function.identity()));
    Map<Long, List<FightPlayerDomain>> fightParts =
        fightPlayerRepo.findByMatchId(matchId).stream()
            .collect(Collectors.groupingBy(FightPlayerDomain::getPlayerSlot));
    List<PlayerPhaseSummaryDomain> rows = new ArrayList<>();
    for (Long slot : new TreeMap<>(spans).keySet()) {
      for (PhaseWindows.Window window : windows) {
        rows.add(
            row(
                matchId,
                slot,
                window,
                spans.get(slot),
                minutes.getOrDefault(slot, List.of()),
                timelines.getOrDefault(slot, List.of()),
                fights,
                fightParts.getOrDefault(slot, List.of())));
      }
    }
    summaryRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    summaryRepo.deleteByMatchId(matchId);
  }

  private PlayerPhaseSummaryDomain row(
      long matchId,
      long slot,
      PhaseWindows.Window window,
      List<ActivityTime.Span> spans,
      List<ReplayPlayerMinuteDomain> minutes,
      List<PlayerTimelineDomain> timeline,
      Map<Integer, FightDomain> fights,
      List<FightPlayerDomain> parts) {
    Map<Activity, Integer> seconds =
        ActivityTime.within(spans, window.startSeconds(), window.endSeconds());
    int known = seconds.values().stream().mapToInt(Integer::intValue).sum();
    int from = window.startSeconds() / 60;
    int to = (int) Math.ceil(window.endSeconds() / 60.0);
    List<FightPlayerDomain> inPhase =
        parts.stream()
            .filter(
                p -> {
                  FightDomain f = fights.get(p.getFightIndex());
                  return f != null
                      && f.getStartSeconds() >= window.startSeconds()
                      && f.getStartSeconds() < window.endSeconds()
                      && !p.getPresence().equals("ABSENT_DEAD");
                })
            .toList();
    return PlayerPhaseSummaryDomain.builder()
        .matchId(matchId)
        .playerSlot(slot)
        .phase(window.name())
        .startSeconds(window.startSeconds())
        .endSeconds(window.endSeconds())
        .unknownSeconds(Math.max(0, window.length() - known))
        .deadSeconds(seconds.getOrDefault(Activity.DEAD, 0))
        .teleportingSeconds(seconds.getOrDefault(Activity.TELEPORTING, 0))
        .inBaseSeconds(seconds.getOrDefault(Activity.IN_BASE, 0))
        .jungleFarmSeconds(seconds.getOrDefault(Activity.JUNGLE_FARM, 0))
        .laneFarmSeconds(seconds.getOrDefault(Activity.LANE_FARM, 0))
        .pushingSeconds(seconds.getOrDefault(Activity.PUSHING, 0))
        .travelingSeconds(seconds.getOrDefault(Activity.TRAVELING, 0))
        .positionedSeconds(seconds.getOrDefault(Activity.POSITIONED, 0))
        .kills(rise(minutes, ReplayPlayerMinuteDomain::getKills, from, to))
        .deaths(rise(minutes, ReplayPlayerMinuteDomain::getDeaths, from, to))
        .assists(rise(minutes, ReplayPlayerMinuteDomain::getAssists, from, to))
        .goldPerMinute(perMinute(timeline, PlayerTimelineDomain::getGold, from, to))
        .xpPerMinute(perMinute(timeline, PlayerTimelineDomain::getXp, from, to))
        .fightsAvailable(inPhase.size())
        .fightsJoined((int) inPhase.stream().filter(p -> JOINED.contains(p.getPresence())).count())
        .analysisVersion(version())
        .build();
  }

  /** How much a cumulative counter grew from one minute to another; null without the counter. */
  private static Integer rise(
      List<ReplayPlayerMinuteDomain> minutes,
      Function<ReplayPlayerMinuteDomain, Long> counter,
      int from,
      int to) {
    Long start = valueAtOrBefore(minutes, counter, from);
    Long end = valueAtOrBefore(minutes, counter, to);
    return start == null || end == null ? null : (int) (end - start);
  }

  private static Long valueAtOrBefore(
      List<ReplayPlayerMinuteDomain> minutes,
      Function<ReplayPlayerMinuteDomain, Long> counter,
      int minute) {
    return minutes.stream()
        .filter(m -> m.getGameMinute() <= minute && counter.apply(m) != null)
        .max(java.util.Comparator.comparingInt(ReplayPlayerMinuteDomain::getGameMinute))
        .map(counter)
        .orElse(minute <= 0 ? Long.valueOf(0) : null);
  }

  private static Double perMinute(
      List<PlayerTimelineDomain> timeline,
      Function<PlayerTimelineDomain, Long> series,
      int from,
      int to) {
    Long start = seriesAt(timeline, series, from);
    Long end = seriesAt(timeline, series, to);
    if (start == null || end == null || to <= from) {
      return null;
    }
    return Math.round((end - start) / (double) (to - from) * 10) / 10.0;
  }

  private static Long seriesAt(
      List<PlayerTimelineDomain> timeline,
      Function<PlayerTimelineDomain, Long> series,
      int minute) {
    return timeline.stream()
        .filter(
            t ->
                t.getGameMinute() != null && t.getGameMinute() <= minute && series.apply(t) != null)
        .max(java.util.Comparator.comparingInt(PlayerTimelineDomain::getGameMinute))
        .map(series)
        .orElse(null);
  }
}
