package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import java.util.List;

/**
 * What one {@code /skills} question found: the subject's own row and the rows that explain it (a
 * hero's skills, the hero a skill is best on), best first.
 */
public record SkillAnswer(SkillPickStatDomain subject, List<SkillPickStatDomain> details) {}
