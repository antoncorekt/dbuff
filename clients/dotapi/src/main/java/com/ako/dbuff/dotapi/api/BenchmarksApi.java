package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;

import com.ako.dbuff.dotapi.model.BenchmarksResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class BenchmarksApi {
  private ApiClient apiClient;

  public BenchmarksApi() {
    this(Configuration.getDefaultApiClient());
  }

  public BenchmarksApi(ApiClient apiClient) {
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
   * GET /benchmarks
   * Benchmarks of average stat values for a hero
   * @param heroId Hero ID (required)
   * @return BenchmarksResponse
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public BenchmarksResponse getBenchmarks(@jakarta.annotation.Nonnull String heroId) throws ApiException {
    return getBenchmarksWithHttpInfo(heroId).getData();
  }

  /**
   * GET /benchmarks
   * Benchmarks of average stat values for a hero
   * @param heroId Hero ID (required)
   * @return ApiResponse&lt;BenchmarksResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<BenchmarksResponse> getBenchmarksWithHttpInfo(@jakarta.annotation.Nonnull String heroId) throws ApiException {
    // Check required parameters
    if (heroId == null) {
      throw new ApiException(400, "Missing the required parameter 'heroId' when calling getBenchmarks");
    }

    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "hero_id", heroId)
    );

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<BenchmarksResponse> localVarReturnType = new GenericType<BenchmarksResponse>() {};
    return apiClient.invokeAPI("BenchmarksApi.getBenchmarks", "/benchmarks", "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
