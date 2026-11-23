package com.ako.dbuff.service.ask;

import com.ako.dbuff.dao.repo.AskTurnRepo;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The daily question cap of a channel and the turn cap of a thread. */
@Component
@RequiredArgsConstructor
public class AskQuota {

  private final AskTurnRepo turnRepo;
  private final AskProperties properties;

  public boolean canAsk(String instanceId, Instant now) {
    LocalDate day = now.atZone(ZoneOffset.UTC).toLocalDate();
    Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant to = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return turnRepo.countQuestions(instanceId, from, to)
        < properties.getMaxQuestionsPerChannelPerDay();
  }

  public boolean threadFull(String threadId) {
    return turnRepo.countByThreadIdAndUserQuestionTrue(threadId)
        >= properties.getMaxTurnsPerThread();
  }
}
