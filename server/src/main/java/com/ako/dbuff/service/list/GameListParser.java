package com.ako.dbuff.service.list;

import com.ako.dbuff.dao.model.MatchDomain;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

@Service
public class GameListParser {

  public List<MatchDomain> parse(Document document) {
    Element listTable = document.select("tbody").first();

    List<MatchDomain> matchDomainList = new ArrayList<MatchDomain>();

    for (Element matchLine : listTable.children()) {
      MatchDomain matchDomain = new MatchDomain();
      Element linkChild = matchLine.children().get(1);

      String href = linkChild.select("a").first().attr("href");
      String gameId = href.substring(href.lastIndexOf('/') + 1);

      matchDomain.setId(Long.parseLong(gameId));

      matchDomainList.add(matchDomain);
    }

    return matchDomainList;
  }
}
