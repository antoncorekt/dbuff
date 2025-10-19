package com.ako.dbuff.service.match.mapper;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dotapi.model.MatchResponse;

public interface MatchInfoMapper {

  void handle(MatchResponse matchResponse, MatchDomain matchDomain);
}
