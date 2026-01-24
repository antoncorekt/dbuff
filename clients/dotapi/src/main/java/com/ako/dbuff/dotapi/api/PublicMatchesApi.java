package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;

import com.ako.dbuff.dotapi.model.PublicMatchesResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class PublicMatchesApi {
  private ApiClient apiClient;

  public PublicMatchesApi() {
    this(Configuration.getDefaultApiClient());
  }

  public PublicMatchesApi(ApiClient apiClient) {
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
   * GET /publicMatches
   * Get list of randomly sampled public matches
   * @param lessThanMatchId Get matches with a match ID lower than this value (optional)
   * @param minRank Minimum rank for the matches. Ranks are represented by integers (10-15: Herald, 20-25: Guardian, 30-35: Crusader, 40-45: Archon, 50-55: Legend, 60-65: Ancient, 70-75: Divine, 80: Immortal). Each increment represents an additional star. (optional)
   * @param maxRank Maximum rank for the matches. Ranks are represented by integers (10-15: Herald, 20-25: Guardian, 30-35: Crusader, 40-45: Archon, 50-55: Legend, 60-65: Ancient, 70-75: Divine, 80: Immortal). Each increment represents an additional star. (optional)
   * @return List&lt;PublicMatchesResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<PublicMatchesResponse> getPublicMatches(@jakarta.annotation.Nullable Long lessThanMatchId, @jakarta.annotation.Nullable Long minRank, @jakarta.annotation.Nullable Long maxRank) throws ApiException {
    return getPublicMatchesWithHttpInfo(lessThanMatchId, minRank, maxRank).getData();
  }

  /**
   * GET /publicMatches
   * Get list of randomly sampled public matches
   * @param lessThanMatchId Get matches with a match ID lower than this value (optional)
   * @param minRank Minimum rank for the matches. Ranks are represented by integers (10-15: Herald, 20-25: Guardian, 30-35: Crusader, 40-45: Archon, 50-55: Legend, 60-65: Ancient, 70-75: Divine, 80: Immortal). Each increment represents an additional star. (optional)
   * @param maxRank Maximum rank for the matches. Ranks are represented by integers (10-15: Herald, 20-25: Guardian, 30-35: Crusader, 40-45: Archon, 50-55: Legend, 60-65: Ancient, 70-75: Divine, 80: Immortal). Each increment represents an additional star. (optional)
   * @return ApiResponse&lt;List&lt;PublicMatchesResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<PublicMatchesResponse>> getPublicMatchesWithHttpInfo(@jakarta.annotation.Nullable Long lessThanMatchId, @jakarta.annotation.Nullable Long minRank, @jakarta.annotation.Nullable Long maxRank) throws ApiException {
    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "less_than_match_id", lessThanMatchId)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "min_rank", minRank));
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "max_rank", maxRank));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<PublicMatchesResponse>> localVarReturnType = new GenericType<List<PublicMatchesResponse>>() {};
    return apiClient.invokeAPI("PublicMatchesApi.getPublicMatches", "/publicMatches", "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
