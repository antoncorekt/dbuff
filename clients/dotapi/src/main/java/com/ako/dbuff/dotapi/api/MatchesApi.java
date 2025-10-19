package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;

import com.ako.dbuff.dotapi.model.MatchResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class MatchesApi {
  private ApiClient apiClient;

  public MatchesApi() {
    this(Configuration.getDefaultApiClient());
  }

  public MatchesApi(ApiClient apiClient) {
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
   * GET /matches/{match_id}
   * Match data
   * @param matchId  (required)
   * @return MatchResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public MatchResponse getMatchesByMatchId(@jakarta.annotation.Nonnull Long matchId) throws ApiException {
    return getMatchesByMatchIdWithHttpInfo(matchId).getData();
  }

  /**
   * GET /matches/{match_id}
   * Match data
   * @param matchId  (required)
   * @return ApiResponse&lt;MatchResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<MatchResponse> getMatchesByMatchIdWithHttpInfo(@jakarta.annotation.Nonnull Long matchId) throws ApiException {
    // Check required parameters
    if (matchId == null) {
      throw new ApiException(400, "Missing the required parameter 'matchId' when calling getMatchesByMatchId");
    }

    // Path parameters
    String localVarPath = "/matches/{match_id}"
            .replaceAll("\\{match_id}", apiClient.escapeString(matchId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<MatchResponse> localVarReturnType = new GenericType<MatchResponse>() {};
    return apiClient.invokeAPI("MatchesApi.getMatchesByMatchId", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
