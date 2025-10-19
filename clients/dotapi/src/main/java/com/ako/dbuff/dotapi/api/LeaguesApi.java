package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;

import com.ako.dbuff.dotapi.model.LeagueObjectResponse;
import com.ako.dbuff.dotapi.model.MatchObjectResponse;
import com.ako.dbuff.dotapi.model.TeamObjectResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class LeaguesApi {
  private ApiClient apiClient;

  public LeaguesApi() {
    this(Configuration.getDefaultApiClient());
  }

  public LeaguesApi(ApiClient apiClient) {
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
   * GET /leagues
   * Get league data
   * @return List&lt;LeagueObjectResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<LeagueObjectResponse> getLeagues() throws ApiException {
    return getLeaguesWithHttpInfo().getData();
  }

  /**
   * GET /leagues
   * Get league data
   * @return ApiResponse&lt;List&lt;LeagueObjectResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<LeagueObjectResponse>> getLeaguesWithHttpInfo() throws ApiException {
    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<LeagueObjectResponse>> localVarReturnType = new GenericType<List<LeagueObjectResponse>>() {};
    return apiClient.invokeAPI("LeaguesApi.getLeagues", "/leagues", "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /leagues/{league_id}
   * Get data for a league
   * @param leagueId League ID (required)
   * @return List&lt;LeagueObjectResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<LeagueObjectResponse> getLeaguesByLeagueId(@jakarta.annotation.Nonnull Long leagueId) throws ApiException {
    return getLeaguesByLeagueIdWithHttpInfo(leagueId).getData();
  }

  /**
   * GET /leagues/{league_id}
   * Get data for a league
   * @param leagueId League ID (required)
   * @return ApiResponse&lt;List&lt;LeagueObjectResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<LeagueObjectResponse>> getLeaguesByLeagueIdWithHttpInfo(@jakarta.annotation.Nonnull Long leagueId) throws ApiException {
    // Check required parameters
    if (leagueId == null) {
      throw new ApiException(400, "Missing the required parameter 'leagueId' when calling getLeaguesByLeagueId");
    }

    // Path parameters
    String localVarPath = "/leagues/{league_id}"
            .replaceAll("\\{league_id}", apiClient.escapeString(leagueId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<LeagueObjectResponse>> localVarReturnType = new GenericType<List<LeagueObjectResponse>>() {};
    return apiClient.invokeAPI("LeaguesApi.getLeaguesByLeagueId", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /leagues/{league_id}/matches
   * Get matches for a league
   * @param leagueId League ID (required)
   * @return MatchObjectResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public MatchObjectResponse getLeaguesByLeagueIdSelectMatches(@jakarta.annotation.Nonnull Long leagueId) throws ApiException {
    return getLeaguesByLeagueIdSelectMatchesWithHttpInfo(leagueId).getData();
  }

  /**
   * GET /leagues/{league_id}/matches
   * Get matches for a league
   * @param leagueId League ID (required)
   * @return ApiResponse&lt;MatchObjectResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<MatchObjectResponse> getLeaguesByLeagueIdSelectMatchesWithHttpInfo(@jakarta.annotation.Nonnull Long leagueId) throws ApiException {
    // Check required parameters
    if (leagueId == null) {
      throw new ApiException(400, "Missing the required parameter 'leagueId' when calling getLeaguesByLeagueIdSelectMatches");
    }

    // Path parameters
    String localVarPath = "/leagues/{league_id}/matches"
            .replaceAll("\\{league_id}", apiClient.escapeString(leagueId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<MatchObjectResponse> localVarReturnType = new GenericType<MatchObjectResponse>() {};
    return apiClient.invokeAPI("LeaguesApi.getLeaguesByLeagueIdSelectMatches", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /leagues/{league_id}/teams
   * Get teams for a league
   * @param leagueId League ID (required)
   * @return TeamObjectResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public TeamObjectResponse getLeaguesByLeagueIdSelectTeams(@jakarta.annotation.Nonnull Long leagueId) throws ApiException {
    return getLeaguesByLeagueIdSelectTeamsWithHttpInfo(leagueId).getData();
  }

  /**
   * GET /leagues/{league_id}/teams
   * Get teams for a league
   * @param leagueId League ID (required)
   * @return ApiResponse&lt;TeamObjectResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<TeamObjectResponse> getLeaguesByLeagueIdSelectTeamsWithHttpInfo(@jakarta.annotation.Nonnull Long leagueId) throws ApiException {
    // Check required parameters
    if (leagueId == null) {
      throw new ApiException(400, "Missing the required parameter 'leagueId' when calling getLeaguesByLeagueIdSelectTeams");
    }

    // Path parameters
    String localVarPath = "/leagues/{league_id}/teams"
            .replaceAll("\\{league_id}", apiClient.escapeString(leagueId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<TeamObjectResponse> localVarReturnType = new GenericType<TeamObjectResponse>() {};
    return apiClient.invokeAPI("LeaguesApi.getLeaguesByLeagueIdSelectTeams", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
