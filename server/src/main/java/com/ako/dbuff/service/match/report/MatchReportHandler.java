package com.ako.dbuff.service.match.report;

public interface MatchReportHandler {

  String handle(MatchReportContext context);

  int getOrder();

  String getHandlerName();
}
