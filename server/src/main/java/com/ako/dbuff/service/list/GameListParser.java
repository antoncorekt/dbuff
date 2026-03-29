package com.ako.dbuff.service.list;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class GameListParser {

  private final ConstantsManagers constantManagers;

  public List<MatchDomain> parse(Document document) {
    Element listTable = document.select("tbody").first();

    if (listTable == null || listTable.children().isEmpty()) {
      log.warn("No match rows found in page. HTML title: {}", document.title());
      return List.of();
    }

    List<MatchDomain> matchDomainList = new ArrayList<MatchDomain>();

    for (Element matchLine : listTable.children()) {
      MatchDomain matchDomain = new MatchDomain();
      Element linkChild = matchLine.children().get(1);

      String href = linkChild.select("a").first().attr("href");
      String gameId = href.substring(href.lastIndexOf('/') + 1);

      Element gameMode = matchLine.children().get(4);

      String gameModeText = gameMode.select("div").first().text();

      MatchTypeConstant matchTypeConstant =
          constantManagers.getMatchTypeByNameConstantMap().get(gameModeText.toLowerCase());
      if (matchTypeConstant == null) {
        log.error("Could not find game mode for {}", gameModeText);
      } else {
        matchDomain.setGameModeId(Long.valueOf(matchTypeConstant.getId()));
        matchDomain.setGameModeName(matchTypeConstant.getName());
      }

      matchDomain.setId(Long.parseLong(gameId));

      matchDomainList.add(matchDomain);
    }

    return matchDomainList;
  }
}
