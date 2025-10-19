package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;

import com.ako.dbuff.dotapi.model.MatchObjectResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class ProMatchesApi {
  private ApiClient apiClient;

  public ProMatchesApi() {
    this(Configuration.getDefaultApiClient());
  }

  public ProMatchesApi(ApiClient apiClient) {
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
   * GET /proMatches
   * Get list of pro matches
   * @param lessThanMatchId Get matches with a match ID lower than this value (optional)
   * @return List&lt;MatchObjectResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<MatchObjectResponse> getProMatches(@jakarta.annotation.Nullable Long lessThanMatchId) throws ApiException {
    return getProMatchesWithHttpInfo(lessThanMatchId).getData();
  }

  /**
   * GET /proMatches
   * Get list of pro matches
   * @param lessThanMatchId Get matches with a match ID lower than this value (optional)
   * @return ApiResponse&lt;List&lt;MatchObjectResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<MatchObjectResponse>> getProMatchesWithHttpInfo(@jakarta.annotation.Nullable Long lessThanMatchId) throws ApiException {
    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "less_than_match_id", lessThanMatchId)
    );

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<MatchObjectResponse>> localVarReturnType = new GenericType<List<MatchObjectResponse>>() {};
    return apiClient.invokeAPI("ProMatchesApi.getProMatches", "/proMatches", "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
