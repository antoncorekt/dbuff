package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.AiReasonsStatus;
import com.ako.dbuff.dao.model.MatchAiReasonsDomain;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchAiReasonsRepo extends JpaRepository<MatchAiReasonsDomain, Long> {

  Optional<MatchAiReasonsDomain> findFirstByMatchIdAndSideAndFactsHashAndStatusOrderByCreatedAtDesc(
      Long matchId, String side, String factsHash, AiReasonsStatus status);

  long countByMatchIdAndCreatedAtAfter(Long matchId, LocalDateTime since);
}
