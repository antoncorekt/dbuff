package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;

import com.ako.dbuff.dotapi.model.ParsedMatchesResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class ParsedMatchesApi {
  private ApiClient apiClient;

  public ParsedMatchesApi() {
    this(Configuration.getDefaultApiClient());
  }

  public ParsedMatchesApi(ApiClient apiClient) {
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
   * GET /parsedMatches
   * Get list of parsed match IDs
   * @param lessThanMatchId Get matches with a match ID lower than this value (optional)
   * @return List&lt;ParsedMatchesResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<ParsedMatchesResponse> getParsedMatches(@jakarta.annotation.Nullable Long lessThanMatchId) throws ApiException {
    return getParsedMatchesWithHttpInfo(lessThanMatchId).getData();
  }

  /**
   * GET /parsedMatches
   * Get list of parsed match IDs
   * @param lessThanMatchId Get matches with a match ID lower than this value (optional)
   * @return ApiResponse&lt;List&lt;ParsedMatchesResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<ParsedMatchesResponse>> getParsedMatchesWithHttpInfo(@jakarta.annotation.Nullable Long lessThanMatchId) throws ApiException {
    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "less_than_match_id", lessThanMatchId)
    );

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<ParsedMatchesResponse>> localVarReturnType = new GenericType<List<ParsedMatchesResponse>>() {};
    return apiClient.invokeAPI("ParsedMatchesApi.getParsedMatches", "/parsedMatches", "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
