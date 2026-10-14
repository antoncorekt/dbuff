package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.dao.model.AiReasonsStatus;
import com.ako.dbuff.dao.model.MatchAiReasonsDomain;
import com.ako.dbuff.dao.repo.MatchAiReasonsRepo;
import com.ako.dbuff.service.ai.reasons.ReasonsResult.Status;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The top reasons a tracked team won or lost a match: builds the facts, reuses a stored answer for
 * the same facts, otherwise asks the model (within the per-match daily call limit) and keeps only
 * the validated reasons. A failed call stores a FAILED mark, which counts toward the limit and is
 * never reused, and returns nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReasonsService {
  private static final TypeReference<List<Reason>> REASONS = new TypeReference<>() {};

  private final ReplayReportAiProperties properties;
  private final AiMatchFactsBuilder factsBuilder;
  private final FactTrimmer trimmer;
  private final ReasonsPromptBuilder promptBuilder;
  private final ReasonsModel model;
  private final ReasonsParser parser;
  private final MatchAiReasonsRepo repo;
  private final ObjectMapper mapper;

  private final Map<Long, Object> matchLocks = new ConcurrentHashMap<>();
  private final AtomicBoolean warnedNoKey = new AtomicBoolean();

  public AiReasonsAvailability availability() {
    if (!properties.isEnabled()) {
      return AiReasonsAvailability.SWITCHED_OFF;
    }
    return model.available() ? AiReasonsAvailability.AVAILABLE : AiReasonsAvailability.NO_API_KEY;
  }

  /** The models that may be asked for: the default one and the configured allow-list. */
  public List<String> allowedModels() {
    Set<String> allowed = new LinkedHashSet<>();
    allowed.add(model.defaultModel());
    if (properties.getAllowedModels() != null) {
      allowed.addAll(properties.getAllowedModels());
    }
    return List.copyOf(allowed);
  }

  /** Whether the model may be asked for; null or blank means the default model, always allowed. */
  public boolean isModelAllowed(String modelName) {
    return modelName == null || modelName.isBlank() || allowedModels().contains(modelName.strip());
  }

  /**
   * The reasons by the model the input names, else the default one. A model outside the allow-list
   * is refused; the stored answer of another model is never reused.
   */
  public ReasonsResult reasons(ReplayReportInput input) {
    String requested = input.modelOverride();
    String chosen =
        requested == null || requested.isBlank() ? model.defaultModel() : requested.strip();
    AiReasonsAvailability availability = availability();
    if (availability == AiReasonsAvailability.SWITCHED_OFF) {
      return ReasonsResult.of(Status.SWITCHED_OFF);
    }
    if (availability == AiReasonsAvailability.NO_API_KEY) {
      if (warnedNoKey.compareAndSet(false, true)) {
        log.warn("AI reasons asked for but no OpenAI API key is configured");
      }
      return ReasonsResult.of(Status.NO_API_KEY);
    }
    if (!isModelAllowed(chosen)) {
      return ReasonsResult.of(Status.MODEL_NOT_ALLOWED);
    }
    TeamSide yours = TeamSide.commonSide(input.focusSlots());
    if (yours == null) {
      return ReasonsResult.of(Status.NO_TEAM);
    }
    List<Fact> facts = factsBuilder.build(input);
    if (facts.isEmpty()) {
      return ReasonsResult.of(Status.NO_FACTS);
    }
    FactTrimmer.Trimmed trimmed = trimmer.trim(facts, properties.getFactsTokenBudget());
    ReasonsPrompt prompt = promptBuilder.build(trimmed);
    String hash = hash(prompt, chosen);
    synchronized (matchLocks.computeIfAbsent(input.matchId(), id -> new Object())) {
      return answer(input.matchId(), yours, trimmed, prompt, hash, chosen);
    }
  }

  private ReasonsResult answer(
      long matchId,
      TeamSide yours,
      FactTrimmer.Trimmed trimmed,
      ReasonsPrompt prompt,
      String hash,
      String chosen) {
    Optional<List<Reason>> stored = stored(matchId, yours, hash);
    if (stored.isPresent()) {
      return new ReasonsResult(Status.CACHED, stored.get(), chosen);
    }
    long calls =
        repo.countByMatchIdAndCreatedAtAfter(
            matchId, LocalDateTime.now().minus(Duration.ofDays(1)));
    if (calls >= properties.getMaxCallsPerMatchPerDay()) {
      log.info(
          "AI reasons of match {} not asked: {} calls in the last day, limit {}",
          matchId,
          calls,
          properties.getMaxCallsPerMatchPerDay());
      return ReasonsResult.of(Status.CALL_LIMIT);
    }
    Set<String> ids = trimmed.kept().stream().map(Fact::id).collect(Collectors.toSet());
    try {
      ModelAnswer answer = model.ask(prompt, chosen);
      List<Reason> reasons = parser.parse(answer.text(), ids);
      save(matchId, yours, hash, answer, toJson(reasons), AiReasonsStatus.DONE);
      log.info("AI reasons of match {}: {} reasons", matchId, reasons.size());
      return new ReasonsResult(Status.READY, reasons, answer.model());
    } catch (RuntimeException e) {
      log.warn("AI reasons of match {} failed: {}", matchId, e.getMessage());
      save(matchId, yours, hash, new ModelAnswer(null, null, chosen), null, AiReasonsStatus.FAILED);
      return ReasonsResult.of(Status.FAILED);
    }
  }

  private Optional<List<Reason>> stored(long matchId, TeamSide yours, String hash) {
    return repo.findFirstByMatchIdAndSideAndFactsHashAndStatusOrderByCreatedAtDesc(
            matchId, yours.name(), hash, AiReasonsStatus.DONE)
        .flatMap(
            row -> {
              try {
                return Optional.of(mapper.readValue(row.getReasonsJson(), REASONS));
              } catch (JsonProcessingException | IllegalArgumentException e) {
                log.warn("Stored AI reasons of match {} unreadable: {}", matchId, e.getMessage());
                return Optional.empty();
              }
            });
  }

  private void save(
      long matchId,
      TeamSide yours,
      String hash,
      ModelAnswer answer,
      String json,
      AiReasonsStatus status) {
    repo.save(
        MatchAiReasonsDomain.builder()
            .matchId(matchId)
            .side(yours.name())
            .factsHash(hash)
            .reasonsJson(json)
            .model(answer.model())
            .tokensUsed(answer.tokensUsed())
            .createdAt(LocalDateTime.now())
            .status(status)
            .build());
  }

  private String toJson(List<Reason> reasons) {
    try {
      return mapper.writeValueAsString(reasons);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("reasons cannot be stored", e);
    }
  }

  /** The prompt carries the facts and the language; the model name is part of the question too. */
  private String hash(ReasonsPrompt prompt, String chosen) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(
                  (chosen + "\n" + prompt.system() + "\n" + prompt.user())
                      .getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
