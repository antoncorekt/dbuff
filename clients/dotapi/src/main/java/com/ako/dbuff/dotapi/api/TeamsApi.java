package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;

import com.ako.dbuff.dotapi.model.TeamHeroesResponse;
import com.ako.dbuff.dotapi.model.TeamMatchObjectResponse;
import com.ako.dbuff.dotapi.model.TeamObjectResponse;
import com.ako.dbuff.dotapi.model.TeamPlayersResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class TeamsApi {
  private ApiClient apiClient;

  public TeamsApi() {
    this(Configuration.getDefaultApiClient());
  }

  public TeamsApi(ApiClient apiClient) {
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
   * GET /teams
   * Get team data
   * @param page Page number, zero indexed. Each page returns up to 1000 entries. (optional)
   * @return List&lt;TeamObjectResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<TeamObjectResponse> getTeams(@jakarta.annotation.Nullable Long page) throws ApiException {
    return getTeamsWithHttpInfo(page).getData();
  }

  /**
   * GET /teams
   * Get team data
   * @param page Page number, zero indexed. Each page returns up to 1000 entries. (optional)
   * @return ApiResponse&lt;List&lt;TeamObjectResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<TeamObjectResponse>> getTeamsWithHttpInfo(@jakarta.annotation.Nullable Long page) throws ApiException {
    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "page", page)
    );

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<TeamObjectResponse>> localVarReturnType = new GenericType<List<TeamObjectResponse>>() {};
    return apiClient.invokeAPI("TeamsApi.getTeams", "/teams", "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /teams/{team_id}
   * Get data for a team
   * @param teamId Team ID (required)
   * @return TeamObjectResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public TeamObjectResponse getTeamsByTeamId(@jakarta.annotation.Nonnull Long teamId) throws ApiException {
    return getTeamsByTeamIdWithHttpInfo(teamId).getData();
  }

  /**
   * GET /teams/{team_id}
   * Get data for a team
   * @param teamId Team ID (required)
   * @return ApiResponse&lt;TeamObjectResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<TeamObjectResponse> getTeamsByTeamIdWithHttpInfo(@jakarta.annotation.Nonnull Long teamId) throws ApiException {
    // Check required parameters
    if (teamId == null) {
      throw new ApiException(400, "Missing the required parameter 'teamId' when calling getTeamsByTeamId");
    }

    // Path parameters
    String localVarPath = "/teams/{team_id}"
            .replaceAll("\\{team_id}", apiClient.escapeString(teamId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<TeamObjectResponse> localVarReturnType = new GenericType<TeamObjectResponse>() {};
    return apiClient.invokeAPI("TeamsApi.getTeamsByTeamId", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /teams/{team_id}/heroes
   * Get heroes for a team
   * @param teamId Team ID (required)
   * @return TeamHeroesResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public TeamHeroesResponse getTeamsByTeamIdSelectHeroes(@jakarta.annotation.Nonnull Long teamId) throws ApiException {
    return getTeamsByTeamIdSelectHeroesWithHttpInfo(teamId).getData();
  }

  /**
   * GET /teams/{team_id}/heroes
   * Get heroes for a team
   * @param teamId Team ID (required)
   * @return ApiResponse&lt;TeamHeroesResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<TeamHeroesResponse> getTeamsByTeamIdSelectHeroesWithHttpInfo(@jakarta.annotation.Nonnull Long teamId) throws ApiException {
    // Check required parameters
    if (teamId == null) {
      throw new ApiException(400, "Missing the required parameter 'teamId' when calling getTeamsByTeamIdSelectHeroes");
    }

    // Path parameters
    String localVarPath = "/teams/{team_id}/heroes"
            .replaceAll("\\{team_id}", apiClient.escapeString(teamId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<TeamHeroesResponse> localVarReturnType = new GenericType<TeamHeroesResponse>() {};
    return apiClient.invokeAPI("TeamsApi.getTeamsByTeamIdSelectHeroes", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /teams/{team_id}/matches
   * Get matches for a team
   * @param teamId Team ID (required)
   * @return TeamMatchObjectResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public TeamMatchObjectResponse getTeamsByTeamIdSelectMatches(@jakarta.annotation.Nonnull Long teamId) throws ApiException {
    return getTeamsByTeamIdSelectMatchesWithHttpInfo(teamId).getData();
  }

  /**
   * GET /teams/{team_id}/matches
   * Get matches for a team
   * @param teamId Team ID (required)
   * @return ApiResponse&lt;TeamMatchObjectResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<TeamMatchObjectResponse> getTeamsByTeamIdSelectMatchesWithHttpInfo(@jakarta.annotation.Nonnull Long teamId) throws ApiException {
    // Check required parameters
    if (teamId == null) {
      throw new ApiException(400, "Missing the required parameter 'teamId' when calling getTeamsByTeamIdSelectMatches");
    }

    // Path parameters
    String localVarPath = "/teams/{team_id}/matches"
            .replaceAll("\\{team_id}", apiClient.escapeString(teamId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<TeamMatchObjectResponse> localVarReturnType = new GenericType<TeamMatchObjectResponse>() {};
    return apiClient.invokeAPI("TeamsApi.getTeamsByTeamIdSelectMatches", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /teams/{team_id}/players
   * Get players who have played for a team
   * @param teamId Team ID (required)
   * @return TeamPlayersResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public TeamPlayersResponse getTeamsByTeamIdSelectPlayers(@jakarta.annotation.Nonnull Long teamId) throws ApiException {
    return getTeamsByTeamIdSelectPlayersWithHttpInfo(teamId).getData();
  }

  /**
   * GET /teams/{team_id}/players
   * Get players who have played for a team
   * @param teamId Team ID (required)
   * @return ApiResponse&lt;TeamPlayersResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<TeamPlayersResponse> getTeamsByTeamIdSelectPlayersWithHttpInfo(@jakarta.annotation.Nonnull Long teamId) throws ApiException {
    // Check required parameters
    if (teamId == null) {
      throw new ApiException(400, "Missing the required parameter 'teamId' when calling getTeamsByTeamIdSelectPlayers");
    }

    // Path parameters
    String localVarPath = "/teams/{team_id}/players"
            .replaceAll("\\{team_id}", apiClient.escapeString(teamId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<TeamPlayersResponse> localVarReturnType = new GenericType<TeamPlayersResponse>() {};
    return apiClient.invokeAPI("TeamsApi.getTeamsByTeamIdSelectPlayers", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
