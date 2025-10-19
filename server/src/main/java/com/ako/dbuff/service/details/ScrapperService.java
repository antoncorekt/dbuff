package com.ako.dbuff.service.details;

import com.ako.dbuff.dao.model.MatchDomain;
import org.jsoup.nodes.Document;

public interface ScrapperService {

  Document scrap(MatchDomain matchDomain);
}
