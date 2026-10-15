package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.dao.repo.SkillPickStatRepo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The one short transaction of the analyzer: what a match had is replaced by the new rows. */
@Component
@RequiredArgsConstructor
public class SkillPickStatStore {
  private final SkillPickStatRepo repo;

  @Transactional
  public void replace(long matchId, List<SkillPickStatDomain> rows) {
    repo.deleteByMatchId(matchId);
    repo.saveAll(rows);
  }
}
