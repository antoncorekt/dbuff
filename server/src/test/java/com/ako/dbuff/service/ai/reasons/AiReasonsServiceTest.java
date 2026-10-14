package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.dao.model.AiReasonsStatus;
import com.ako.dbuff.dao.model.MatchAiReasonsDomain;
import com.ako.dbuff.dao.repo.MatchAiReasonsRepo;
import com.ako.dbuff.service.ai.reasons.ReasonsResult.Status;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReasonsServiceTest {
  private static final String GOOD =
      "[{\"rank\":1,\"title\":\"Won the fights\",\"explanation\":\"Took every big fight.\","
          + "\"evidence\":[\"F1\",\"RESULT\"]}]";

  private final ReplayReportAiProperties properties = new ReplayReportAiProperties();
  private final FakeReasonsModel model = new FakeReasonsModel();
  private final MatchAiReasonsRepo repo = mock(MatchAiReasonsRepo.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private AiReasonsService service;

  @BeforeEach
  void setUp() {
    properties.setEnabled(true);
    model.answer = GOOD;
    service =
        new AiReasonsService(
            properties,
            ReasonsFixtures.builder(
                ReasonsFixtures.section("important-fights", null, "8:10 Dire won the fight")),
            new FactTrimmer(ReasonsFixtures.openAi()),
            new ReasonsPromptBuilder(properties),
            model,
            new ReasonsParser(mapper),
            repo,
            mapper);
  }

  @Test
  void aFirstCallAsksTheModelAndStoresTheValidatedReasons() {
    ReasonsResult result = service.reasons(ReasonsFixtures.input());

    assertThat(result.status()).isEqualTo(Status.READY);
    assertThat(result.reasons()).hasSize(1);
    assertThat(model.asked).hasSize(1);
    assertThat(model.asked.get(0).user()).contains("F1: 8:10 Dire won the fight");
    ArgumentCaptor<MatchAiReasonsDomain> saved =
        ArgumentCaptor.forClass(MatchAiReasonsDomain.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(AiReasonsStatus.DONE);
    assertThat(saved.getValue().getSide()).isEqualTo("DIRE");
    assertThat(saved.getValue().getTokensUsed()).isEqualTo(321);
    assertThat(saved.getValue().getModel()).isEqualTo("fake-model");
    assertThat(saved.getValue().getFactsHash()).hasSize(64);
    assertThat(saved.getValue().getReasonsJson()).contains("Won the fights");
  }

  @Test
  void theSameFactsReuseTheStoredAnswerWithNoModelCall() {
    service.reasons(ReasonsFixtures.input());
    ArgumentCaptor<MatchAiReasonsDomain> saved =
        ArgumentCaptor.forClass(MatchAiReasonsDomain.class);
    verify(repo).save(saved.capture());
    when(repo.findFirstByMatchIdAndSideAndFactsHashAndStatusOrderByCreatedAtDesc(
            eq(ReasonsFixtures.MATCH_ID),
            eq("DIRE"),
            eq(saved.getValue().getFactsHash()),
            eq(AiReasonsStatus.DONE)))
        .thenReturn(Optional.of(saved.getValue()));
    model.asked.clear();

    ReasonsResult again = service.reasons(ReasonsFixtures.input());

    assertThat(again.status()).isEqualTo(Status.CACHED);
    assertThat(again.reasons()).extracting(Reason::title).containsExactly("Won the fights");
    assertThat(model.asked).isEmpty();
  }

  @Test
  void changedFactsAreAskedAgain() {
    service.reasons(ReasonsFixtures.input());
    service.reasons(ReasonsFixtures.input());
    properties.setLanguage("Russian");
    service.reasons(ReasonsFixtures.input());

    ArgumentCaptor<MatchAiReasonsDomain> saved =
        ArgumentCaptor.forClass(MatchAiReasonsDomain.class);
    verify(repo, org.mockito.Mockito.times(3)).save(saved.capture());
    assertThat(saved.getAllValues().get(0).getFactsHash())
        .isEqualTo(saved.getAllValues().get(1).getFactsHash());
    assertThat(saved.getAllValues().get(2).getFactsHash())
        .isNotEqualTo(saved.getAllValues().get(0).getFactsHash());
  }

  @Test
  void thePerMatchDailyCallLimitStopsTheModelCall() {
    properties.setMaxCallsPerMatchPerDay(2);
    when(repo.countByMatchIdAndCreatedAtAfter(
            eq(ReasonsFixtures.MATCH_ID), any(LocalDateTime.class)))
        .thenReturn(2L);

    ReasonsResult result = service.reasons(ReasonsFixtures.input());

    assertThat(result.status()).isEqualTo(Status.CALL_LIMIT);
    assertThat(model.asked).isEmpty();
    verify(repo, never()).save(any());
  }

  @Test
  void aStoredAnswerIsStillServedWhenTheLimitIsReached() {
    service.reasons(ReasonsFixtures.input());
    ArgumentCaptor<MatchAiReasonsDomain> saved =
        ArgumentCaptor.forClass(MatchAiReasonsDomain.class);
    verify(repo).save(saved.capture());
    when(repo.findFirstByMatchIdAndSideAndFactsHashAndStatusOrderByCreatedAtDesc(
            anyLong(), any(), any(), eq(AiReasonsStatus.DONE)))
        .thenReturn(Optional.of(saved.getValue()));
    when(repo.countByMatchIdAndCreatedAtAfter(anyLong(), any())).thenReturn(99L);

    assertThat(service.reasons(ReasonsFixtures.input()).status()).isEqualTo(Status.CACHED);
  }

  @Test
  void theSwitchOffMeansNothingIsBuiltOrCalled() {
    properties.setEnabled(false);

    assertThat(service.reasons(ReasonsFixtures.input()).status()).isEqualTo(Status.SWITCHED_OFF);
    assertThat(service.availability()).isEqualTo(AiReasonsAvailability.SWITCHED_OFF);
    assertThat(model.asked).isEmpty();
    org.mockito.Mockito.verifyNoInteractions(repo);
  }

  @Test
  void noApiKeyMeansNothingIsCalled() {
    model.available = false;

    assertThat(service.reasons(ReasonsFixtures.input()).status()).isEqualTo(Status.NO_API_KEY);
    assertThat(service.reasons(ReasonsFixtures.input()).status()).isEqualTo(Status.NO_API_KEY);
    assertThat(service.availability()).isEqualTo(AiReasonsAvailability.NO_API_KEY);
    assertThat(model.asked).isEmpty();
    org.mockito.Mockito.verifyNoInteractions(repo);
  }

  @Test
  void aTrackedTeamOnBothSidesOrNoneHasNoSideToJudge() {
    var both =
        new com.ako.dbuff.service.replay.report.ReplayReportInput(
            ReasonsFixtures.MATCH_ID,
            ReasonsFixtures.match(),
            java.util.Set.of(0L, 128L),
            java.util.Map.of(),
            3);

    assertThat(service.reasons(both).status()).isEqualTo(Status.NO_TEAM);
    assertThat(model.asked).isEmpty();
  }

  @Test
  void aFailedCallStoresOnlyAMarkThatCountsTowardTheLimit_andPostsNothing() {
    model.failure = new ReasonsModelException("timeout", null);

    ReasonsResult result = service.reasons(ReasonsFixtures.input());

    assertThat(result.status()).isEqualTo(Status.FAILED);
    assertThat(result.reasons()).isEmpty();
    ArgumentCaptor<MatchAiReasonsDomain> saved =
        ArgumentCaptor.forClass(MatchAiReasonsDomain.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(AiReasonsStatus.FAILED);
    assertThat(saved.getValue().getReasonsJson()).isNull();
  }

  @Test
  void anAnswerWithBadJsonOrNoValidEvidenceFails() {
    model.answer = "sorry, I cannot";
    assertThat(service.reasons(ReasonsFixtures.input()).status()).isEqualTo(Status.FAILED);

    model.answer =
        "[{\"rank\":1,\"title\":\"T\",\"explanation\":\"E\",\"evidence\":[\"MADE_UP\"]}]";
    assertThat(service.reasons(ReasonsFixtures.input()).status()).isEqualTo(Status.FAILED);

    ArgumentCaptor<MatchAiReasonsDomain> saved =
        ArgumentCaptor.forClass(MatchAiReasonsDomain.class);
    verify(repo, org.mockito.Mockito.times(2)).save(saved.capture());
    assertThat(saved.getAllValues()).allMatch(r -> r.getStatus() == AiReasonsStatus.FAILED);
  }

  @Test
  void aNoLongerReadableStoredAnswerIsAskedForAgain() {
    when(repo.findFirstByMatchIdAndSideAndFactsHashAndStatusOrderByCreatedAtDesc(
            anyLong(), any(), any(), eq(AiReasonsStatus.DONE)))
        .thenReturn(Optional.of(MatchAiReasonsDomain.builder().reasonsJson("not json").build()));

    assertThat(service.reasons(ReasonsFixtures.input()).status()).isEqualTo(Status.READY);
    assertThat(model.asked).hasSize(1);
  }

  @Test
  void theFactsAreTrimmedToTheBudgetBeforeTheCall() {
    properties.setFactsTokenBudget(500);
    var big =
        ReasonsFixtures.builder(
            ReasonsFixtures.section(
                "rotations", null, "x".repeat(3000), "y".repeat(3000), "z".repeat(3000)));
    service =
        new AiReasonsService(
            properties,
            big,
            new FactTrimmer(ReasonsFixtures.openAi()),
            new ReasonsPromptBuilder(properties),
            model,
            new ReasonsParser(mapper),
            repo,
            mapper);
    model.answer = "[{\"rank\":1,\"title\":\"T\",\"explanation\":\"E\",\"evidence\":[\"RESULT\"]}]";

    service.reasons(ReasonsFixtures.input());

    assertThat(model.asked.get(0).user())
        .contains("RESULT:")
        .doesNotContain("xxx")
        .contains("less important facts were left out");
    assertThat(List.of(model.asked.get(0).user().length())).allMatch(n -> n < 2500);
  }
}
