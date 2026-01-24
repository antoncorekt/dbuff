package com.ako.dbuff.service.list;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.executors.Executors;
import com.ako.dbuff.service.match.MatchProcessorService;
import com.ako.dbuff.utils.Utils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class AllHistoryService {

  private final GameListScrapper gameListScrapper;
  private final GameListParser gameListParser;
  private final MatchProcessorService matchProcessorService;
  private final Semaphore pageScrapingSemaphore;

  @AllArgsConstructor
  @NoArgsConstructor
  @Data
  @Builder
  public static class ScrapperParams {
    int startPage;
    int endPage;
  }

  /**
   * Finds all matches for a user and processes them in parallel.
   * Uses ScopedValue context for logging - playerId should already be in scope
   * from the calling HistoryResources.
   * 
   * Algorithm:
   * 1. Fetch first page to determine total page count
   * 2. Process all pages in parallel using virtual threads (limited by semaphore)
   * 3. Each page processing runs with PAGE_NUM in scope for logging
   *
   * @param userId the user ID
   * @param gameMode the game mode filter
   * @param scrapperParams pagination parameters
   * @return list of all found matches
   */
  public List<MatchDomain> findAllUserMatches(
      long userId, long gameMode, ScrapperParams scrapperParams) {

    String ctx = ProcessContext.getContextString();
    
    int startPage = Utils.nvl(scrapperParams.getStartPage(), 1);
    int endPage = Utils.nvl(scrapperParams.getEndPage(), -1);

    // Step 1: Fetch first page to determine end page if not specified
    if (endPage == -1) {
      log.info("{} Fetching first page to determine total page count for user {}", ctx, userId);
      Document firstDocument = gameListScrapper.scrap(userId, startPage);
      endPage = findEndPage(firstDocument);
      log.info("{} Found total {} pages for user {}", ctx, endPage, userId);
    }

    log.info("{} Starting parallel processing of pages {} to {} for user {}", 
        ctx, startPage, endPage, userId);

    // Step 2: Process all pages in parallel using virtual threads (limited by semaphore)
    List<MatchDomain> allUserMatches = Collections.synchronizedList(new ArrayList<>());
    
    final int finalEndPage = endPage;
    List<CompletableFuture<Void>> futures = IntStream.rangeClosed(startPage, finalEndPage)
        .mapToObj(pageNum -> CompletableFuture.runAsync(
            () -> processPage(userId, pageNum, allUserMatches),
            Executors.PROCESSOR_VIRT_EXECUTOR_SERVICE
        ))
        .collect(java.util.stream.Collectors.toList());

    // Wait for all pages to complete
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    log.info("{} Completed processing all {} matches across {} pages for user {}", 
        ctx, allUserMatches.size(), endPage - startPage + 1, userId);
    
    return allUserMatches;
  }

  /**
   * Processes a single page: scrapes, parses, and processes matches.
   * Runs with PAGE_NUM in scope for logging context.
   * Uses semaphore to limit concurrent page processing.
   *
   * @param userId the user ID
   * @param pageNum the page number to process
   * @param allUserMatches the synchronized list to add matches to
   */
  private void processPage(long userId, int pageNum, List<MatchDomain> allUserMatches) {
    ProcessContext.runWithPageNum(pageNum, () -> {
      String ctx = ProcessContext.getContextString();
      
      try {
        // Acquire semaphore permit before processing (blocks if limit reached)
        pageScrapingSemaphore.acquire();
        try {
          log.info("{} Start scraping page {} for user {}", ctx, pageNum, userId);
          
          Document document = gameListScrapper.scrap(userId, pageNum);
          List<MatchDomain> matchesOnPage = gameListParser.parse(document);

          log.info("{} Found {} matches on page {} for user {}", 
              ctx, matchesOnPage.size(), pageNum, userId);
          
          allUserMatches.addAll(matchesOnPage);

          log.info("{} Start processing {} matches from page {} for user {}", 
              ctx, matchesOnPage.size(), pageNum, userId);
          
          matchProcessorService.process(matchesOnPage);
          
          log.info("{} Completed processing page {} for user {}", ctx, pageNum, userId);
        } finally {
          // Always release the permit
          pageScrapingSemaphore.release();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("{} Interrupted while waiting for semaphore for page {} user {}", 
            ctx, pageNum, userId);
      } catch (Exception e) {
        log.error("{} Error processing page {} for user {}: {}", 
            ctx, pageNum, userId, e.getMessage(), e);
        throw new RuntimeException("Failed to process page " + pageNum, e);
      }
    });
  }

  private int findEndPage(Document document) {
    Element lastSpan = document.select("span.last").first();
    if (lastSpan == null || lastSpan.children().isEmpty()) {
      // Only one page exists
      return 1;
    }
    String href = lastSpan.child(0).attr("href");
    return Integer.parseInt(href.substring(href.lastIndexOf("page=") + 5));
  }
}
