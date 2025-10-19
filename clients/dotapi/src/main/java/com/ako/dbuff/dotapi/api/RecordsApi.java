package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;

import com.ako.dbuff.dotapi.model.RecordsResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class RecordsApi {
  private ApiClient apiClient;

  public RecordsApi() {
    this(Configuration.getDefaultApiClient());
  }

  public RecordsApi(ApiClient apiClient) {
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
   * GET /records/{field}
   * Get top performances in a stat
   * @param field Field name to query (required)
   * @return List&lt;RecordsResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<RecordsResponse> getRecordsByField(@jakarta.annotation.Nonnull String field) throws ApiException {
    return getRecordsByFieldWithHttpInfo(field).getData();
  }

  /**
   * GET /records/{field}
   * Get top performances in a stat
   * @param field Field name to query (required)
   * @return ApiResponse&lt;List&lt;RecordsResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<RecordsResponse>> getRecordsByFieldWithHttpInfo(@jakarta.annotation.Nonnull String field) throws ApiException {
    // Check required parameters
    if (field == null) {
      throw new ApiException(400, "Missing the required parameter 'field' when calling getRecordsByField");
    }

    // Path parameters
    String localVarPath = "/records/{field}"
            .replaceAll("\\{field}", apiClient.escapeString(field.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<RecordsResponse>> localVarReturnType = new GenericType<List<RecordsResponse>>() {};
    return apiClient.invokeAPI("RecordsApi.getRecordsByField", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
