package com.ako.dbuff.service.match.analyzer;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import java.util.List;

public class MatchBoundary {

  MatchDomain matchDomain;
  List<PlayerMatchStatisticDomain> statistic;
  List<AbilityDomain> abilities;
  List<ItemDomain> items;
}
