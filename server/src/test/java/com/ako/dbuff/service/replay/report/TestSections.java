package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import java.util.List;

/** Small section stand-ins for tests that care about names and switches, not content. */
public final class TestSections {
  private TestSections() {}

  public static ReplayReportSection named(String name, int order, boolean defaultEnabled) {
    return new ReplayReportSection() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public boolean defaultEnabled() {
        return defaultEnabled;
      }

      @Override
      public int order() {
        return order;
      }

      @Override
      public ReportDetail minimumDetail() {
        return ReportDetail.STANDARD;
      }

      @Override
      public List<String> lines(ReplayReportInput input) {
        return List.of("**" + name + "**", "line of " + name);
      }
    };
  }
}
