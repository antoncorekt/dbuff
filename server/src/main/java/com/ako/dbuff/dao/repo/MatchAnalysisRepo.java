package com.ako.dbuff.dao.repo;

import com.ako.dbuff.dao.model.MatchAnalysisDomain;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for managing AI-generated match analysis results. */
@Repository
public interface MatchAnalysisRepo extends JpaRepository<MatchAnalysisDomain, Long> {

  /**
   * Find all analyses created after a specific timestamp.
   *
   * @param createdAt the timestamp to filter by
   * @return list of analyses created after the given timestamp
   */
  List<MatchAnalysisDomain> findByCreatedAtAfter(LocalDateTime createdAt);

  /**
   * Find all analyses by AI provider.
   *
   * @param aiProvider the AI provider name (e.g., "openai", "gemini")
   * @return list of analyses from the specified provider
   */
  List<MatchAnalysisDomain> findByAiProvider(String aiProvider);

  /**
   * Find all successful analyses.
   *
   * @return list of successful analyses
   */
  List<MatchAnalysisDomain> findBySuccessTrue();

  /**
   * Find all failed analyses.
   *
   * @return list of failed analyses
   */
  List<MatchAnalysisDomain> findBySuccessFalse();
}
