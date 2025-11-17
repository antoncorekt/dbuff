package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;

import com.ako.dbuff.dotapi.model.PlayerCountsResponse;
import com.ako.dbuff.dotapi.model.PlayerHeroesResponse;
import com.ako.dbuff.dotapi.model.PlayerMatchesResponse;
import com.ako.dbuff.dotapi.model.PlayerPeersResponse;
import com.ako.dbuff.dotapi.model.PlayerProsResponse;
import com.ako.dbuff.dotapi.model.PlayerRankingsResponse;
import com.ako.dbuff.dotapi.model.PlayerRatingsResponse;
import com.ako.dbuff.dotapi.model.PlayerRecentMatchesResponse;
import com.ako.dbuff.dotapi.model.PlayerTotalsResponse;
import com.ako.dbuff.dotapi.model.PlayerWardMapResponse;
import com.ako.dbuff.dotapi.model.PlayerWinLossResponse;
import com.ako.dbuff.dotapi.model.PlayerWordCloudResponse;
import com.ako.dbuff.dotapi.model.PlayersResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class PlayersApi {
  private ApiClient apiClient;

  public PlayersApi() {
    this(Configuration.getDefaultApiClient());
  }

  public PlayersApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  /**
   * Get the API client
   *
   * @return API client
   */
  public ApiClient getApiClient() {
    return apiClient;
  }

  /**
   * Set the API client
   *
   * @param apiClient an instance of API client
   */
  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  /**
   * GET /players/{account_id}
   * Player data
   * @param accountId Steam32 account ID (required)
   * @return PlayersResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public PlayersResponse getPlayersByAccountId(@jakarta.annotation.Nonnull Long accountId) throws ApiException {
    return getPlayersByAccountIdWithHttpInfo(accountId).getData();
  }

  /**
   * GET /players/{account_id}
   * Player data
   * @param accountId Steam32 account ID (required)
   * @return ApiResponse&lt;PlayersResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<PlayersResponse> getPlayersByAccountIdWithHttpInfo(@jakarta.annotation.Nonnull Long accountId) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountId");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<PlayersResponse> localVarReturnType = new GenericType<PlayersResponse>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountId", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/histograms
   * Distribution of matches in a single stat
   * @param accountId Steam32 account ID (required)
   * @param field Field to aggregate on (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return List&lt;Object&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<Object> getPlayersByAccountIdHistogramsByField(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nonnull String field, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    return getPlayersByAccountIdHistogramsByFieldWithHttpInfo(accountId, field, limit, offset, win, patch, gameMode, lobbyType, region, date, laneRole, heroId, isRadiant, includedAccountId, excludedAccountId, withHeroId, againstHeroId, significant, having, sort).getData();
  }

  /**
   * GET /players/{account_id}/histograms
   * Distribution of matches in a single stat
   * @param accountId Steam32 account ID (required)
   * @param field Field to aggregate on (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return ApiResponse&lt;List&lt;Object&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<Object>> getPlayersByAccountIdHistogramsByFieldWithHttpInfo(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nonnull String field, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdHistogramsByField");
    }
    if (field == null) {
      throw new ApiException(400, "Missing the required parameter 'field' when calling getPlayersByAccountIdHistogramsByField");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/histograms/{field}"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()))
            .replaceAll("\\{field}", apiClient.escapeString(field.toString()));

    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "limit", limit)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "offset", offset));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "win", win));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "patch", patch));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "game_mode", gameMode));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lobby_type", lobbyType));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "region", region));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "date", date));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lane_role", laneRole));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "hero_id", heroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "is_radiant", isRadiant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "included_account_id", includedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "excluded_account_id", excludedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "with_hero_id", withHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "against_hero_id", againstHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "significant", significant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "having", having));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "sort", sort));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<Object>> localVarReturnType = new GenericType<List<Object>>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdHistogramsByField", localVarPath, "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/counts
   * Counts in categories
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return PlayerCountsResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public PlayerCountsResponse getPlayersByAccountIdSelectCounts(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    return getPlayersByAccountIdSelectCountsWithHttpInfo(accountId, limit, offset, win, patch, gameMode, lobbyType, region, date, laneRole, heroId, isRadiant, includedAccountId, excludedAccountId, withHeroId, againstHeroId, significant, having, sort).getData();
  }

  /**
   * GET /players/{account_id}/counts
   * Counts in categories
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return ApiResponse&lt;PlayerCountsResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<PlayerCountsResponse> getPlayersByAccountIdSelectCountsWithHttpInfo(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdSelectCounts");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/counts"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "limit", limit)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "offset", offset));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "win", win));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "patch", patch));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "game_mode", gameMode));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lobby_type", lobbyType));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "region", region));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "date", date));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lane_role", laneRole));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "hero_id", heroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "is_radiant", isRadiant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "included_account_id", includedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "excluded_account_id", excludedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "with_hero_id", withHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "against_hero_id", againstHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "significant", significant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "having", having));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "sort", sort));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<PlayerCountsResponse> localVarReturnType = new GenericType<PlayerCountsResponse>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdSelectCounts", localVarPath, "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/heroes
   * Heroes played
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return List&lt;PlayerHeroesResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<PlayerHeroesResponse> getPlayersByAccountIdSelectHeroes(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    return getPlayersByAccountIdSelectHeroesWithHttpInfo(accountId, limit, offset, win, patch, gameMode, lobbyType, region, date, laneRole, heroId, isRadiant, includedAccountId, excludedAccountId, withHeroId, againstHeroId, significant, having, sort).getData();
  }

  /**
   * GET /players/{account_id}/heroes
   * Heroes played
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return ApiResponse&lt;List&lt;PlayerHeroesResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<PlayerHeroesResponse>> getPlayersByAccountIdSelectHeroesWithHttpInfo(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdSelectHeroes");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/heroes"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "limit", limit)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "offset", offset));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "win", win));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "patch", patch));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "game_mode", gameMode));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lobby_type", lobbyType));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "region", region));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "date", date));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lane_role", laneRole));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "hero_id", heroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "is_radiant", isRadiant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "included_account_id", includedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "excluded_account_id", excludedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "with_hero_id", withHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "against_hero_id", againstHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "significant", significant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "having", having));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "sort", sort));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<PlayerHeroesResponse>> localVarReturnType = new GenericType<List<PlayerHeroesResponse>>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdSelectHeroes", localVarPath, "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/matches
   * Matches played (full history, and supports column selection)
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @param project Fields to project (array) (optional)
   * @return List&lt;PlayerMatchesResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<PlayerMatchesResponse> getPlayersByAccountIdSelectMatches(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort, @jakarta.annotation.Nullable String project) throws ApiException {
    return getPlayersByAccountIdSelectMatchesWithHttpInfo(accountId, limit, offset, win, patch, gameMode, lobbyType, region, date, laneRole, heroId, isRadiant, includedAccountId, excludedAccountId, withHeroId, againstHeroId, significant, having, sort, project).getData();
  }

  /**
   * GET /players/{account_id}/matches
   * Matches played (full history, and supports column selection)
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @param project Fields to project (array) (optional)
   * @return ApiResponse&lt;List&lt;PlayerMatchesResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<PlayerMatchesResponse>> getPlayersByAccountIdSelectMatchesWithHttpInfo(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort, @jakarta.annotation.Nullable String project) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdSelectMatches");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/matches"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "limit", limit)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "offset", offset));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "win", win));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "patch", patch));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "game_mode", gameMode));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lobby_type", lobbyType));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "region", region));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "date", date));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lane_role", laneRole));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "hero_id", heroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "is_radiant", isRadiant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "included_account_id", includedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "excluded_account_id", excludedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "with_hero_id", withHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "against_hero_id", againstHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "significant", significant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "having", having));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "sort", sort));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "project", project));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<PlayerMatchesResponse>> localVarReturnType = new GenericType<List<PlayerMatchesResponse>>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdSelectMatches", localVarPath, "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/peers
   * Players played with
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return List&lt;PlayerPeersResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<PlayerPeersResponse> getPlayersByAccountIdSelectPeers(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    return getPlayersByAccountIdSelectPeersWithHttpInfo(accountId, limit, offset, win, patch, gameMode, lobbyType, region, date, laneRole, heroId, isRadiant, includedAccountId, excludedAccountId, withHeroId, againstHeroId, significant, having, sort).getData();
  }

  /**
   * GET /players/{account_id}/peers
   * Players played with
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return ApiResponse&lt;List&lt;PlayerPeersResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<PlayerPeersResponse>> getPlayersByAccountIdSelectPeersWithHttpInfo(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdSelectPeers");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/peers"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "limit", limit)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "offset", offset));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "win", win));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "patch", patch));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "game_mode", gameMode));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lobby_type", lobbyType));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "region", region));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "date", date));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lane_role", laneRole));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "hero_id", heroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "is_radiant", isRadiant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "included_account_id", includedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "excluded_account_id", excludedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "with_hero_id", withHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "against_hero_id", againstHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "significant", significant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "having", having));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "sort", sort));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<PlayerPeersResponse>> localVarReturnType = new GenericType<List<PlayerPeersResponse>>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdSelectPeers", localVarPath, "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/pros
   * Pro players played with
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return List&lt;PlayerProsResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<PlayerProsResponse> getPlayersByAccountIdSelectPros(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    return getPlayersByAccountIdSelectProsWithHttpInfo(accountId, limit, offset, win, patch, gameMode, lobbyType, region, date, laneRole, heroId, isRadiant, includedAccountId, excludedAccountId, withHeroId, againstHeroId, significant, having, sort).getData();
  }

  /**
   * GET /players/{account_id}/pros
   * Pro players played with
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return ApiResponse&lt;List&lt;PlayerProsResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<PlayerProsResponse>> getPlayersByAccountIdSelectProsWithHttpInfo(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdSelectPros");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/pros"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "limit", limit)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "offset", offset));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "win", win));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "patch", patch));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "game_mode", gameMode));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lobby_type", lobbyType));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "region", region));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "date", date));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lane_role", laneRole));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "hero_id", heroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "is_radiant", isRadiant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "included_account_id", includedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "excluded_account_id", excludedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "with_hero_id", withHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "against_hero_id", againstHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "significant", significant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "having", having));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "sort", sort));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<PlayerProsResponse>> localVarReturnType = new GenericType<List<PlayerProsResponse>>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdSelectPros", localVarPath, "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/rankings
   * Player hero rankings
   * @param accountId Steam32 account ID (required)
   * @return List&lt;PlayerRankingsResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<PlayerRankingsResponse> getPlayersByAccountIdSelectRankings(@jakarta.annotation.Nonnull Long accountId) throws ApiException {
    return getPlayersByAccountIdSelectRankingsWithHttpInfo(accountId).getData();
  }

  /**
   * GET /players/{account_id}/rankings
   * Player hero rankings
   * @param accountId Steam32 account ID (required)
   * @return ApiResponse&lt;List&lt;PlayerRankingsResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<PlayerRankingsResponse>> getPlayersByAccountIdSelectRankingsWithHttpInfo(@jakarta.annotation.Nonnull Long accountId) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdSelectRankings");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/rankings"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<PlayerRankingsResponse>> localVarReturnType = new GenericType<List<PlayerRankingsResponse>>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdSelectRankings", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/ratings
   * Player rating history
   * @param accountId Steam32 account ID (required)
   * @return List&lt;PlayerRatingsResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<PlayerRatingsResponse> getPlayersByAccountIdSelectRatings(@jakarta.annotation.Nonnull Long accountId) throws ApiException {
    return getPlayersByAccountIdSelectRatingsWithHttpInfo(accountId).getData();
  }

  /**
   * GET /players/{account_id}/ratings
   * Player rating history
   * @param accountId Steam32 account ID (required)
   * @return ApiResponse&lt;List&lt;PlayerRatingsResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<PlayerRatingsResponse>> getPlayersByAccountIdSelectRatingsWithHttpInfo(@jakarta.annotation.Nonnull Long accountId) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdSelectRatings");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/ratings"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<PlayerRatingsResponse>> localVarReturnType = new GenericType<List<PlayerRatingsResponse>>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdSelectRatings", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/recentMatches
   * Recent matches played (limited number of results)
   * @param accountId Steam32 account ID (required)
   * @return List&lt;PlayerRecentMatchesResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<PlayerRecentMatchesResponse> getPlayersByAccountIdSelectRecentMatches(@jakarta.annotation.Nonnull Long accountId) throws ApiException {
    return getPlayersByAccountIdSelectRecentMatchesWithHttpInfo(accountId).getData();
  }

  /**
   * GET /players/{account_id}/recentMatches
   * Recent matches played (limited number of results)
   * @param accountId Steam32 account ID (required)
   * @return ApiResponse&lt;List&lt;PlayerRecentMatchesResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<PlayerRecentMatchesResponse>> getPlayersByAccountIdSelectRecentMatchesWithHttpInfo(@jakarta.annotation.Nonnull Long accountId) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdSelectRecentMatches");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/recentMatches"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<PlayerRecentMatchesResponse>> localVarReturnType = new GenericType<List<PlayerRecentMatchesResponse>>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdSelectRecentMatches", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/totals
   * Totals in stats
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return List&lt;PlayerTotalsResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<PlayerTotalsResponse> getPlayersByAccountIdSelectTotals(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    return getPlayersByAccountIdSelectTotalsWithHttpInfo(accountId, limit, offset, win, patch, gameMode, lobbyType, region, date, laneRole, heroId, isRadiant, includedAccountId, excludedAccountId, withHeroId, againstHeroId, significant, having, sort).getData();
  }

  /**
   * GET /players/{account_id}/totals
   * Totals in stats
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return ApiResponse&lt;List&lt;PlayerTotalsResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<PlayerTotalsResponse>> getPlayersByAccountIdSelectTotalsWithHttpInfo(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdSelectTotals");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/totals"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "limit", limit)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "offset", offset));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "win", win));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "patch", patch));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "game_mode", gameMode));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lobby_type", lobbyType));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "region", region));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "date", date));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lane_role", laneRole));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "hero_id", heroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "is_radiant", isRadiant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "included_account_id", includedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "excluded_account_id", excludedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "with_hero_id", withHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "against_hero_id", againstHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "significant", significant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "having", having));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "sort", sort));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<PlayerTotalsResponse>> localVarReturnType = new GenericType<List<PlayerTotalsResponse>>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdSelectTotals", localVarPath, "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/wardmap
   * Wards placed in matches played
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return PlayerWardMapResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public PlayerWardMapResponse getPlayersByAccountIdSelectWardmap(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    return getPlayersByAccountIdSelectWardmapWithHttpInfo(accountId, limit, offset, win, patch, gameMode, lobbyType, region, date, laneRole, heroId, isRadiant, includedAccountId, excludedAccountId, withHeroId, againstHeroId, significant, having, sort).getData();
  }

  /**
   * GET /players/{account_id}/wardmap
   * Wards placed in matches played
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return ApiResponse&lt;PlayerWardMapResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<PlayerWardMapResponse> getPlayersByAccountIdSelectWardmapWithHttpInfo(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdSelectWardmap");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/wardmap"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "limit", limit)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "offset", offset));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "win", win));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "patch", patch));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "game_mode", gameMode));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lobby_type", lobbyType));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "region", region));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "date", date));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lane_role", laneRole));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "hero_id", heroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "is_radiant", isRadiant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "included_account_id", includedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "excluded_account_id", excludedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "with_hero_id", withHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "against_hero_id", againstHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "significant", significant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "having", having));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "sort", sort));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<PlayerWardMapResponse> localVarReturnType = new GenericType<PlayerWardMapResponse>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdSelectWardmap", localVarPath, "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/wl
   * Win/Loss count
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return PlayerWinLossResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public PlayerWinLossResponse getPlayersByAccountIdSelectWl(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    return getPlayersByAccountIdSelectWlWithHttpInfo(accountId, limit, offset, win, patch, gameMode, lobbyType, region, date, laneRole, heroId, isRadiant, includedAccountId, excludedAccountId, withHeroId, againstHeroId, significant, having, sort).getData();
  }

  /**
   * GET /players/{account_id}/wl
   * Win/Loss count
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return ApiResponse&lt;PlayerWinLossResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<PlayerWinLossResponse> getPlayersByAccountIdSelectWlWithHttpInfo(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdSelectWl");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/wl"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "limit", limit)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "offset", offset));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "win", win));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "patch", patch));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "game_mode", gameMode));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lobby_type", lobbyType));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "region", region));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "date", date));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lane_role", laneRole));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "hero_id", heroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "is_radiant", isRadiant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "included_account_id", includedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "excluded_account_id", excludedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "with_hero_id", withHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "against_hero_id", againstHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "significant", significant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "having", having));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "sort", sort));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<PlayerWinLossResponse> localVarReturnType = new GenericType<PlayerWinLossResponse>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdSelectWl", localVarPath, "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /players/{account_id}/wordcloud
   * Words said/read in matches played
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return PlayerWordCloudResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public PlayerWordCloudResponse getPlayersByAccountIdSelectWordcloud(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    return getPlayersByAccountIdSelectWordcloudWithHttpInfo(accountId, limit, offset, win, patch, gameMode, lobbyType, region, date, laneRole, heroId, isRadiant, includedAccountId, excludedAccountId, withHeroId, againstHeroId, significant, having, sort).getData();
  }

  /**
   * GET /players/{account_id}/wordcloud
   * Words said/read in matches played
   * @param accountId Steam32 account ID (required)
   * @param limit Number of matches to limit to (optional)
   * @param offset Number of matches to offset start by (optional)
   * @param win Whether the player won (optional)
   * @param patch Patch ID, from dotaconstants (optional)
   * @param gameMode Game Mode ID (optional)
   * @param lobbyType Lobby type ID (optional)
   * @param region Region ID (optional)
   * @param date Days previous (optional)
   * @param laneRole Lane Role ID (optional)
   * @param heroId Hero ID (optional)
   * @param isRadiant Whether the player was radiant (optional)
   * @param includedAccountId Account IDs in the match (array) (optional)
   * @param excludedAccountId Account IDs not in the match (array) (optional)
   * @param withHeroId Hero IDs on the player&#39;s team (array) (optional)
   * @param againstHeroId Hero IDs against the player&#39;s team (array) (optional)
   * @param significant Whether the match was significant for aggregation purposes. Defaults to 1 (true), set this to 0 to return data for non-standard modes/matches. (optional)
   * @param having The minimum number of games played, for filtering hero stats (optional)
   * @param sort The field to return matches sorted by in descending order (optional)
   * @return ApiResponse&lt;PlayerWordCloudResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<PlayerWordCloudResponse> getPlayersByAccountIdSelectWordcloudWithHttpInfo(@jakarta.annotation.Nonnull Long accountId, @jakarta.annotation.Nullable Long limit, @jakarta.annotation.Nullable Long offset, @jakarta.annotation.Nullable Long win, @jakarta.annotation.Nullable Long patch, @jakarta.annotation.Nullable Long gameMode, @jakarta.annotation.Nullable Long lobbyType, @jakarta.annotation.Nullable Long region, @jakarta.annotation.Nullable Long date, @jakarta.annotation.Nullable Long laneRole, @jakarta.annotation.Nullable Long heroId, @jakarta.annotation.Nullable Long isRadiant, @jakarta.annotation.Nullable Long includedAccountId, @jakarta.annotation.Nullable Long excludedAccountId, @jakarta.annotation.Nullable Long withHeroId, @jakarta.annotation.Nullable Long againstHeroId, @jakarta.annotation.Nullable Long significant, @jakarta.annotation.Nullable Long having, @jakarta.annotation.Nullable String sort) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling getPlayersByAccountIdSelectWordcloud");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/wordcloud"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "limit", limit)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "offset", offset));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "win", win));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "patch", patch));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "game_mode", gameMode));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lobby_type", lobbyType));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "region", region));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "date", date));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "lane_role", laneRole));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "hero_id", heroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "is_radiant", isRadiant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "included_account_id", includedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "excluded_account_id", excludedAccountId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "with_hero_id", withHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "against_hero_id", againstHeroId));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "significant", significant));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "having", having));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "sort", sort));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<PlayerWordCloudResponse> localVarReturnType = new GenericType<PlayerWordCloudResponse>() {};
    return apiClient.invokeAPI("PlayersApi.getPlayersByAccountIdSelectWordcloud", localVarPath, "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * POST /players/{account_id}/refresh
   * Refresh player match history (up to 500) and medal
   * @param accountId Steam32 account ID (required)
   * @return Object
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public Object postRefresh(@jakarta.annotation.Nonnull Long accountId) throws ApiException {
    return postRefreshWithHttpInfo(accountId).getData();
  }

  /**
   * POST /players/{account_id}/refresh
   * Refresh player match history (up to 500) and medal
   * @param accountId Steam32 account ID (required)
   * @return ApiResponse&lt;Object&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<Object> postRefreshWithHttpInfo(@jakarta.annotation.Nonnull Long accountId) throws ApiException {
    // Check required parameters
    if (accountId == null) {
      throw new ApiException(400, "Missing the required parameter 'accountId' when calling postRefresh");
    }

    // Path parameters
    String localVarPath = "/players/{account_id}/refresh"
            .replaceAll("\\{account_id}", apiClient.escapeString(accountId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<Object> localVarReturnType = new GenericType<Object>() {};
    return apiClient.invokeAPI("PlayersApi.postRefresh", localVarPath, "POST", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
