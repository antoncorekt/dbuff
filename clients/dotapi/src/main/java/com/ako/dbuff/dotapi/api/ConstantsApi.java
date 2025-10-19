package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;

import com.ako.dbuff.dotapi.model.GetConstantsByResource200Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class ConstantsApi {
  private ApiClient apiClient;

  public ConstantsApi() {
    this(Configuration.getDefaultApiClient());
  }

  public ConstantsApi(ApiClient apiClient) {
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
   * GET /constants
   * Get static game data mirrored from the dotaconstants repository.
   * @param resource Resource name e.g. &#x60;heroes&#x60;. [List of resources](https://github.com/odota/dotaconstants/tree/master/build) (required)
   * @return GetConstantsByResource200Response
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public GetConstantsByResource200Response getConstantsByResource(@jakarta.annotation.Nonnull String resource) throws ApiException {
    return getConstantsByResourceWithHttpInfo(resource).getData();
  }

  /**
   * GET /constants
   * Get static game data mirrored from the dotaconstants repository.
   * @param resource Resource name e.g. &#x60;heroes&#x60;. [List of resources](https://github.com/odota/dotaconstants/tree/master/build) (required)
   * @return ApiResponse&lt;GetConstantsByResource200Response&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<GetConstantsByResource200Response> getConstantsByResourceWithHttpInfo(@jakarta.annotation.Nonnull String resource) throws ApiException {
    // Check required parameters
    if (resource == null) {
      throw new ApiException(400, "Missing the required parameter 'resource' when calling getConstantsByResource");
    }

    // Path parameters
    String localVarPath = "/constants/{resource}"
            .replaceAll("\\{resource}", apiClient.escapeString(resource.toString()));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<GetConstantsByResource200Response> localVarReturnType = new GenericType<GetConstantsByResource200Response>() {};
    return apiClient.invokeAPI("ConstantsApi.getConstantsByResource", localVarPath, "GET", new ArrayList<>(), null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
