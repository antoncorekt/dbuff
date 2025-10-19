package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;

import com.ako.dbuff.dotapi.model.HeroDurationsResponse;
import com.ako.dbuff.dotapi.model.HeroItemPopularityResponse;
import com.ako.dbuff.dotapi.model.HeroMatchupsResponse;
import com.ako.dbuff.dotapi.model.HeroObjectResponse;
import com.ako.dbuff.dotapi.model.MatchObjectResponse;
import com.ako.dbuff.dotapi.model.PlayerObjectResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class HeroesApi {
  private ApiClient apiClient;

  public HeroesApi() {
    this(Configuration.getDefaultApiClient());
  }

  public HeroesApi(ApiClient apiClient) {
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
   * GET /heroes
   * Get hero data
   * @return List&lt;HeroObjectResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<HeroObjectResponse> getHeroes() throws ApiException {
    return getHeroesWithHttpInfo().getData();
  }

  /**
   * GET /heroes
   * Get hero data
   * @return ApiResponse&lt;List&lt;HeroObjectResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<HeroObjectResponse>> getHeroesWithHttpInfo() throws ApiException {
    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<HeroObjectResponse>> localVarReturnType = new GenericType<List<HeroObjectResponse>>() {};
    return apiClient.invokeAPI("HeroesApi.getHeroes", "/heroes", "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /heroes/{hero_id}/durations
   * Get hero performance over a range of match durations
   * @param heroId Hero ID (required)
   * @return List&lt;HeroDurationsResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<HeroDurationsResponse> getHeroesByHeroIdSelectDurations(@jakarta.annotation.Nonnull Long heroId) throws ApiException {
    return getHeroesByHeroIdSelectDurationsWithHttpInfo(heroId).getData();
  }

  /**
   * GET /heroes/{hero_id}/durations
   * Get hero performance over a range of match durations
   * @param heroId Hero ID (required)
   * @return ApiResponse&lt;List&lt;HeroDurationsResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<HeroDurationsResponse>> getHeroesByHeroIdSelectDurationsWithHttpInfo(@jakarta.annotation.Nonnull Long heroId) throws ApiException {
    // Check required parameters
    if (heroId == null) {
      throw new ApiException(400, "Missing the required parameter 'heroId' when calling getHeroesByHeroIdSelectDurations");
    }

    // Path parameters
    String localVarPath = "/heroes/{hero_id}/durations"
            .replaceAll("\\{hero_id}", apiClient.escapeString(heroId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<HeroDurationsResponse>> localVarReturnType = new GenericType<List<HeroDurationsResponse>>() {};
    return apiClient.invokeAPI("HeroesApi.getHeroesByHeroIdSelectDurations", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /heroes/{hero_id}/itemPopularity
   * Get item popularity of hero categoried by start, early, mid and late game, analyzed from professional games
   * @param heroId Hero ID (required)
   * @return HeroItemPopularityResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public HeroItemPopularityResponse getHeroesByHeroIdSelectItemPopularity(@jakarta.annotation.Nonnull Long heroId) throws ApiException {
    return getHeroesByHeroIdSelectItemPopularityWithHttpInfo(heroId).getData();
  }

  /**
   * GET /heroes/{hero_id}/itemPopularity
   * Get item popularity of hero categoried by start, early, mid and late game, analyzed from professional games
   * @param heroId Hero ID (required)
   * @return ApiResponse&lt;HeroItemPopularityResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<HeroItemPopularityResponse> getHeroesByHeroIdSelectItemPopularityWithHttpInfo(@jakarta.annotation.Nonnull Long heroId) throws ApiException {
    // Check required parameters
    if (heroId == null) {
      throw new ApiException(400, "Missing the required parameter 'heroId' when calling getHeroesByHeroIdSelectItemPopularity");
    }

    // Path parameters
    String localVarPath = "/heroes/{hero_id}/itemPopularity"
            .replaceAll("\\{hero_id}", apiClient.escapeString(heroId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<HeroItemPopularityResponse> localVarReturnType = new GenericType<HeroItemPopularityResponse>() {};
    return apiClient.invokeAPI("HeroesApi.getHeroesByHeroIdSelectItemPopularity", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /heroes/{hero_id}/matches
   * Get recent matches with a hero
   * @param heroId Hero ID (required)
   * @return List&lt;MatchObjectResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<MatchObjectResponse> getHeroesByHeroIdSelectMatches(@jakarta.annotation.Nonnull Long heroId) throws ApiException {
    return getHeroesByHeroIdSelectMatchesWithHttpInfo(heroId).getData();
  }

  /**
   * GET /heroes/{hero_id}/matches
   * Get recent matches with a hero
   * @param heroId Hero ID (required)
   * @return ApiResponse&lt;List&lt;MatchObjectResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<MatchObjectResponse>> getHeroesByHeroIdSelectMatchesWithHttpInfo(@jakarta.annotation.Nonnull Long heroId) throws ApiException {
    // Check required parameters
    if (heroId == null) {
      throw new ApiException(400, "Missing the required parameter 'heroId' when calling getHeroesByHeroIdSelectMatches");
    }

    // Path parameters
    String localVarPath = "/heroes/{hero_id}/matches"
            .replaceAll("\\{hero_id}", apiClient.escapeString(heroId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<MatchObjectResponse>> localVarReturnType = new GenericType<List<MatchObjectResponse>>() {};
    return apiClient.invokeAPI("HeroesApi.getHeroesByHeroIdSelectMatches", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /heroes/{hero_id}/matchups
   * Get results against other heroes for a hero
   * @param heroId Hero ID (required)
   * @return List&lt;HeroMatchupsResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<HeroMatchupsResponse> getHeroesByHeroIdSelectMatchups(@jakarta.annotation.Nonnull Long heroId) throws ApiException {
    return getHeroesByHeroIdSelectMatchupsWithHttpInfo(heroId).getData();
  }

  /**
   * GET /heroes/{hero_id}/matchups
   * Get results against other heroes for a hero
   * @param heroId Hero ID (required)
   * @return ApiResponse&lt;List&lt;HeroMatchupsResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<HeroMatchupsResponse>> getHeroesByHeroIdSelectMatchupsWithHttpInfo(@jakarta.annotation.Nonnull Long heroId) throws ApiException {
    // Check required parameters
    if (heroId == null) {
      throw new ApiException(400, "Missing the required parameter 'heroId' when calling getHeroesByHeroIdSelectMatchups");
    }

    // Path parameters
    String localVarPath = "/heroes/{hero_id}/matchups"
            .replaceAll("\\{hero_id}", apiClient.escapeString(heroId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<HeroMatchupsResponse>> localVarReturnType = new GenericType<List<HeroMatchupsResponse>>() {};
    return apiClient.invokeAPI("HeroesApi.getHeroesByHeroIdSelectMatchups", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /heroes/{hero_id}/players
   * Get players who have played this hero
   * @param heroId Hero ID (required)
   * @return List&lt;List&lt;PlayerObjectResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<List<PlayerObjectResponse>> getHeroesByHeroIdSelectPlayers(@jakarta.annotation.Nonnull Long heroId) throws ApiException {
    return getHeroesByHeroIdSelectPlayersWithHttpInfo(heroId).getData();
  }

  /**
   * GET /heroes/{hero_id}/players
   * Get players who have played this hero
   * @param heroId Hero ID (required)
   * @return ApiResponse&lt;List&lt;List&lt;PlayerObjectResponse&gt;&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<List<PlayerObjectResponse>>> getHeroesByHeroIdSelectPlayersWithHttpInfo(@jakarta.annotation.Nonnull Long heroId) throws ApiException {
    // Check required parameters
    if (heroId == null) {
      throw new ApiException(400, "Missing the required parameter 'heroId' when calling getHeroesByHeroIdSelectPlayers");
    }

    // Path parameters
    String localVarPath = "/heroes/{hero_id}/players"
            .replaceAll("\\{hero_id}", apiClient.escapeString(heroId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<List<PlayerObjectResponse>>> localVarReturnType = new GenericType<List<List<PlayerObjectResponse>>>() {};
    return apiClient.invokeAPI("HeroesApi.getHeroesByHeroIdSelectPlayers", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
