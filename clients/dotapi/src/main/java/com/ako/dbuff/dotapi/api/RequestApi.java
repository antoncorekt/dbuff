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
public class RequestApi {
  private ApiClient apiClient;

  public RequestApi() {
    this(Configuration.getDefaultApiClient());
  }

  public RequestApi(ApiClient apiClient) {
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
   * GET /request/{jobId}
   * Get parse request state
   * @param jobId The job ID to query. (required)
   * @return Object
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public Object getRequestByJobId(@jakarta.annotation.Nonnull String jobId) throws ApiException {
    return getRequestByJobIdWithHttpInfo(jobId).getData();
  }

  /**
   * GET /request/{jobId}
   * Get parse request state
   * @param jobId The job ID to query. (required)
   * @return ApiResponse&lt;Object&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<Object> getRequestByJobIdWithHttpInfo(@jakarta.annotation.Nonnull String jobId) throws ApiException {
    // Check required parameters
    if (jobId == null) {
      throw new ApiException(400, "Missing the required parameter 'jobId' when calling getRequestByJobId");
    }

    // Path parameters
    String localVarPath = "/request/{jobId}"
            .replaceAll("\\{jobId}", apiClient.escapeString(jobId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<Object> localVarReturnType = new GenericType<Object>() {};
    return apiClient.invokeAPI("RequestApi.getRequestByJobId", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * POST /request/{match_id}
   * Submit a new parse request. This call counts as 10 calls for rate limit (but not billing) purposes.
   * @param matchId  (required)
   * @return Object
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public Object postRequestByJobId(@jakarta.annotation.Nonnull Long matchId) throws ApiException {
    return postRequestByJobIdWithHttpInfo(matchId).getData();
  }

  /**
   * POST /request/{match_id}
   * Submit a new parse request. This call counts as 10 calls for rate limit (but not billing) purposes.
   * @param matchId  (required)
   * @return ApiResponse&lt;Object&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<Object> postRequestByJobIdWithHttpInfo(@jakarta.annotation.Nonnull Long matchId) throws ApiException {
    // Check required parameters
    if (matchId == null) {
      throw new ApiException(400, "Missing the required parameter 'matchId' when calling postRequestByJobId");
    }

    // Path parameters
    String localVarPath = "/request/{match_id}"
            .replaceAll("\\{match_id}", apiClient.escapeString(matchId.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<Object> localVarReturnType = new GenericType<Object>() {};
    return apiClient.invokeAPI("RequestApi.postRequestByJobId", localVarPath, "POST", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
