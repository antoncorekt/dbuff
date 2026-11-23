package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AskTurnDomain;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AskTurnRepo extends JpaRepository<AskTurnDomain, Long> {

  List<AskTurnDomain> findByThreadIdOrderBySeqAsc(String threadId);

  long countByThreadIdAndUserQuestionTrue(String threadId);

  @Query(
      "select count(t) from AskTurnDomain t, AskThreadDomain th"
          + " where t.threadId = th.threadId and th.instanceId = :instanceId"
          + " and t.userQuestion = true and t.createdAt >= :from and t.createdAt < :to")
  long countQuestions(
      @Param("instanceId") String instanceId, @Param("from") Instant from, @Param("to") Instant to);
}
