package com.ako.dbuff.service.match.analyzer.handlers;

import com.ako.dbuff.service.match.analyzer.AnalyzerResult;
import com.ako.dbuff.service.match.analyzer.MatchBoundary;

public interface AnalyzerHandler {

  AnalyzerResult analyze(MatchBoundary matchBoundary);
}
