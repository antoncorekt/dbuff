package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class FindMatchesApi {
  private ApiClient apiClient;

  public FindMatchesApi() {
    this(Configuration.getDefaultApiClient());
  }

  public FindMatchesApi(ApiClient apiClient) {
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
   * GET /
   * Finds recent matches by heroes played
   * @param teamA Hero IDs on first team (array) (optional)
   * @param teamB Hero IDs on second team (array) (optional)
   * @return List&lt;Object&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<Object> getFindMatches(@jakarta.annotation.Nullable List<Long> teamA, @jakarta.annotation.Nullable List<Long> teamB) throws ApiException {
    return getFindMatchesWithHttpInfo(teamA, teamB).getData();
  }

  /**
   * GET /
   * Finds recent matches by heroes played
   * @param teamA Hero IDs on first team (array) (optional)
   * @param teamB Hero IDs on second team (array) (optional)
   * @return ApiResponse&lt;List&lt;Object&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<Object>> getFindMatchesWithHttpInfo(@jakarta.annotation.Nullable List<Long> teamA, @jakarta.annotation.Nullable List<Long> teamB) throws ApiException {
    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("csv", "teamA", teamA)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("csv", "teamB", teamB));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<Object>> localVarReturnType = new GenericType<List<Object>>() {};
    return apiClient.invokeAPI("FindMatchesApi.getFindMatches", "/findMatches", "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
