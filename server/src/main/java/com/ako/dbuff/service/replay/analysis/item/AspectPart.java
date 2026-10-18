package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.List;
import java.util.Map;

/**
 * What one aspect concluded about one hero and one entry.
 *
 * @param gain the gain in gold
 * @param measured false when the data could not tell, so the entry has no ratio
 * @param figures named totals; figures of one name from several aspects add up
 * @param flags what the data could not tell, as codes
 * @param rows one per use or per measured stretch; rows of the same label, second and target from
 *     several aspects are one row whose figures are added
 * @param count the uses the aspect counted; the entry takes the first aspect's
 * @param paybackSeconds when the gain passed the cost, for an aspect that tracks it; else null
 * @param goldPerMinute the gain per minute owned that may pay the cost back; null when none
 */
public record AspectPart(
    double gain,
    boolean measured,
    Map<String, Double> figures,
    List<String> flags,
    List<Detail> rows,
    int count,
    Double paybackSeconds,
    Double goldPerMinute) {}
