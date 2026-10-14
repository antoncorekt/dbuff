package com.ako.dbuff.service.match;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import com.ako.dbuff.service.match.DotaApiLastMatchesService.RecentMatch;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator;
import com.ako.dbuff.service.scheduler.MatchParseSchedulerService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class LastMatchesProcessorServiceTest {

  private static final long PLAYER = 204429164L;
  private static final long ABILITY_DRAFT = 18L;
  private static final LocalDateTime TRACKING_SINCE = LocalDateTime.of(2026, 5, 1, 12, 0);

  private DotaApiLastMatchesService lastMatches;
  private MatchProcessorService matchProcessor;
  private MatchRepo matchRepo;
  private MatchReportOrchestrator orchestrator;
  private DbufInstanceConfigService instanceConfigService;
  private DotaApiParseRequestService parseRequests;
  private MatchParseSchedulerService parseScheduler;
  private LastMatchesProcessorService service;

  @BeforeEach
  void setUp() {
    lastMatches = Mockito.mock(DotaApiLastMatchesService.class);
    matchProcessor = Mockito.mock(MatchProcessorService.class);
    matchRepo = Mockito.mock(MatchRepo.class);
    orchestrator = Mockito.mock(MatchReportOrchestrator.class);
    instanceConfigService = Mockito.mock(DbufInstanceConfigService.class);
    parseRequests = Mockito.mock(DotaApiParseRequestService.class);
    parseScheduler = Mockito.mock(MatchParseSchedulerService.class);

    Mockito.when(matchRepo.findById(Mockito.anyLong())).thenReturn(Optional.empty());
    Mockito.when(matchRepo.save(Mockito.any())).thenAnswer(call -> call.getArgument(0));
    Mockito.when(matchProcessor.process(Mockito.anyList()))
        .thenAnswer(
            call -> CompletableFuture.completedFuture(call.<List<MatchDomain>>getArgument(0)));

    service =
        new LastMatchesProcessorService(
            lastMatches,
            matchProcessor,
            matchRepo,
            orchestrator,
            instanceConfigService,
            parseRequests,
            parseScheduler);
  }

  private static long epoch(LocalDateTime time) {
    return time.atZone(ZoneId.systemDefault()).toEpochSecond();
  }

  private void instance(LocalDateTime trackingSince, Set<Long> modes) {
    DbufInstanceConfigDomain config =
        DbufInstanceConfigDomain.builder()
            .id("instance-1")
            .discordChannelId("channel-1")
            .active(true)
            .players(Set.of(PlayerDomain.builder().id(PLAYER).name("Tigress").build()))
            .gameModeIds(modes)
            .trackingSince(trackingSince)
            .build();
    Mockito.when(instanceConfigService.getDomainById("instance-1")).thenReturn(Optional.of(config));
  }

  private static final long NEWCOMER = 279195408L;

  /** Tracks PLAYER since {@code TRACKING_SINCE} and NEWCOMER since {@code newcomerSince}. */
  private void instanceWithNewcomer(LocalDateTime newcomerSince) {
    DbufInstanceConfigDomain config =
        DbufInstanceConfigDomain.builder()
            .id("instance-1")
            .discordChannelId("channel-1")
            .active(true)
            .players(
                Set.of(
                    PlayerDomain.builder().id(PLAYER).name("Tigress").build(),
                    PlayerDomain.builder().id(NEWCOMER).name("Newcomer").build()))
            .gameModeIds(Set.of())
            .trackingSince(TRACKING_SINCE)
            .playerTrackingSince(Map.of(NEWCOMER, newcomerSince))
            .build();
    Mockito.when(instanceConfigService.getDomainById("instance-1")).thenReturn(Optional.of(config));
  }

  private RecentMatch match(long id, LocalDateTime start) {
    return new RecentMatch(id, epoch(start), 1800L, ABILITY_DRAFT);
  }

  @Test
  void addingAPlayerLater_doesNotSwallowAnExistingPlayersRecentMatch() {
    LocalDateTime newcomerSince = TRACKING_SINCE.plusDays(2);
    instanceWithNewcomer(newcomerSince);
    // Ended after the instance's cutoff but before the newcomer was added.
    Mockito.when(lastMatches.fetchLastMatches(PLAYER))
        .thenReturn(List.of(match(10, newcomerSince.minusHours(3))));
    Mockito.when(lastMatches.fetchLastMatches(NEWCOMER)).thenReturn(List.of());

    service.processLastMatchesForInstance("instance-1");

    Mockito.verify(parseRequests).submitParseRequest(10L);
    Mockito.verify(matchProcessor, Mockito.never()).process(Mockito.anyList());
  }

  @Test
  void addingAPlayerLater_importsOnlyTheNewPlayersHistoryQuietly() {
    LocalDateTime newcomerSince = TRACKING_SINCE.plusDays(2);
    instanceWithNewcomer(newcomerSince);
    Mockito.when(lastMatches.fetchLastMatches(PLAYER)).thenReturn(List.of());
    Mockito.when(lastMatches.fetchLastMatches(NEWCOMER))
        .thenReturn(List.of(match(11, newcomerSince.minusDays(1))));

    service.processLastMatchesForInstance("instance-1");

    Mockito.verify(matchProcessor).process(Mockito.argThat(list -> list.size() == 1));
    Mockito.verify(parseRequests, Mockito.never()).submitParseRequest(Mockito.anyLong());
  }

  @Test
  void aMatchBothPlayersHave_isLiveIfItIsNewsToEither() {
    LocalDateTime newcomerSince = TRACKING_SINCE.plusDays(2);
    instanceWithNewcomer(newcomerSince);
    RecentMatch shared = match(12, newcomerSince.minusHours(3));
    Mockito.when(lastMatches.fetchLastMatches(PLAYER)).thenReturn(List.of(shared));
    Mockito.when(lastMatches.fetchLastMatches(NEWCOMER)).thenReturn(List.of(shared));

    service.processLastMatchesForInstance("instance-1");

    Mockito.verify(parseRequests).submitParseRequest(12L);
    Mockito.verify(matchProcessor, Mockito.never()).process(Mockito.anyList());
  }

  private void recent(long id, LocalDateTime start, long mode) {
    Mockito.when(lastMatches.fetchLastMatches(PLAYER))
        .thenReturn(List.of(new RecentMatch(id, epoch(start), 1800L, mode)));
  }

  @Test
  void matchThatEndedBeforeTracking_isImportedQuietly() {
    instance(TRACKING_SINCE, Set.of());
    recent(1, TRACKING_SINCE.minusDays(2), ABILITY_DRAFT);

    service.processLastMatchesForInstance("instance-1");

    Mockito.verify(matchProcessor).process(Mockito.argThat(list -> list.size() == 1));
    Mockito.verify(orchestrator).sendBackfillNotice(Mockito.any(), Mockito.eq(1));
    Mockito.verify(parseRequests, Mockito.never()).submitParseRequest(Mockito.anyLong());
    Mockito.verify(orchestrator, Mockito.never())
        .processAndReportPartial(Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  void matchThatEndedAfterTracking_isReportedLive() {
    instance(TRACKING_SINCE, Set.of());
    recent(2, TRACKING_SINCE.plusMinutes(10), ABILITY_DRAFT);

    service.processLastMatchesForInstance("instance-1");

    Mockito.verify(parseRequests).submitParseRequest(2L);
    Mockito.verify(orchestrator)
        .processAndReportPartial(Mockito.any(), Mockito.any(), Mockito.any());
    Mockito.verify(matchProcessor, Mockito.never()).process(Mockito.anyList());
  }

  @Test
  void matchThatStartedBeforeButEndedAfterTracking_isReportedLive() {
    instance(TRACKING_SINCE, Set.of());
    Mockito.when(lastMatches.fetchLastMatches(PLAYER))
        .thenReturn(
            List.of(
                new RecentMatch(3, epoch(TRACKING_SINCE.minusMinutes(10)), 3600L, ABILITY_DRAFT)));

    service.processLastMatchesForInstance("instance-1");

    Mockito.verify(parseRequests).submitParseRequest(3L);
    Mockito.verify(matchProcessor, Mockito.never()).process(Mockito.anyList());
  }

  @Test
  void instanceWithoutTrackingSince_keepsReportingEverything() {
    instance(null, Set.of());
    recent(4, TRACKING_SINCE.minusDays(30), ABILITY_DRAFT);

    service.processLastMatchesForInstance("instance-1");

    Mockito.verify(parseRequests).submitParseRequest(4L);
    Mockito.verify(matchProcessor, Mockito.never()).process(Mockito.anyList());
  }

  @Test
  void gameModeFilter_appliesToAMatchSeenForTheFirstTime() {
    instance(TRACKING_SINCE, Set.of(ABILITY_DRAFT));
    recent(5, TRACKING_SINCE.plusMinutes(10), 22L);

    service.processLastMatchesForInstance("instance-1");

    Mockito.verify(parseRequests, Mockito.never()).submitParseRequest(Mockito.anyLong());
    Mockito.verify(matchProcessor, Mockito.never()).process(Mockito.anyList());
  }

  @Test
  void newMatchRecordsItsGameModeAtCreation() {
    instance(TRACKING_SINCE, Set.of());
    recent(6, TRACKING_SINCE.plusMinutes(10), ABILITY_DRAFT);

    service.processLastMatchesForInstance("instance-1");

    ArgumentCaptor<MatchDomain> saved = ArgumentCaptor.forClass(MatchDomain.class);
    Mockito.verify(matchRepo, Mockito.atLeastOnce()).save(saved.capture());
    assertThat(saved.getAllValues())
        .anyMatch(m -> m.getGameModeId() != null && m.getGameModeId() == ABILITY_DRAFT);
  }

  @Test
  void failedQuietImport_doesNotStopLiveReports() {
    instance(TRACKING_SINCE, Set.of());
    Mockito.when(lastMatches.fetchLastMatches(PLAYER))
        .thenReturn(
            List.of(
                new RecentMatch(7, epoch(TRACKING_SINCE.minusDays(1)), 1800L, ABILITY_DRAFT),
                new RecentMatch(8, epoch(TRACKING_SINCE.plusMinutes(10)), 1800L, ABILITY_DRAFT)));
    Mockito.when(matchProcessor.process(Mockito.anyList()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

    service.processLastMatchesForInstance("instance-1");

    Mockito.verify(parseRequests).submitParseRequest(8L);
  }

  @Test
  void aLiveMatchIsReportedWithWhatTheRecentMatchesListSaid() {
    instance(TRACKING_SINCE, Set.of());
    DotaApiLastMatchesService.PlayerLine line =
        new DotaApiLastMatchesService.PlayerLine(PLAYER, 0L, 14L, 1L, 2L, 3L, 400L, 500L, true);
    Mockito.when(lastMatches.fetchLastMatches(PLAYER))
        .thenReturn(
            List.of(
                new RecentMatch(
                    3, epoch(TRACKING_SINCE.plusMinutes(10)), 1800L, ABILITY_DRAFT, line)));

    service.processLastMatchesForInstance("instance-1");

    Mockito.verify(orchestrator)
        .processAndReportPartial(
            Mockito.any(),
            Mockito.any(),
            Mockito.argThat(
                summary ->
                    summary.duration() == 1800L
                        && summary.gameMode() == ABILITY_DRAFT
                        && summary.lines().equals(List.of(line))));
  }
}
