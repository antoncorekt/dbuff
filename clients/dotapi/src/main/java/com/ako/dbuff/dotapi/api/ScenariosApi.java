package com.ako.dbuff.dotapi.api;

import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.ApiResponse;
import com.ako.dbuff.dotapi.invoker.Configuration;
import com.ako.dbuff.dotapi.invoker.Pair;

import jakarta.ws.rs.core.GenericType;

import com.ako.dbuff.dotapi.model.ScenarioItemTimingsResponse;
import com.ako.dbuff.dotapi.model.ScenarioLaneRolesResponse;
import com.ako.dbuff.dotapi.model.ScenarioMiscResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.14.0")
public class ScenariosApi {
  private ApiClient apiClient;

  public ScenariosApi() {
    this(Configuration.getDefaultApiClient());
  }

  public ScenariosApi(ApiClient apiClient) {
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
   * GET /scenarios/itemTimings
   * Win rates for certain item timings on a hero for items that cost at least 1400 gold
   * @param item Filter by item name e.g. \&quot;spirit_vessel\&quot; (optional)
   * @param heroId Hero ID (optional)
   * @return List&lt;ScenarioItemTimingsResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<ScenarioItemTimingsResponse> getScenariosItemTimings(@jakarta.annotation.Nullable String item, @jakarta.annotation.Nullable Long heroId) throws ApiException {
    return getScenariosItemTimingsWithHttpInfo(item, heroId).getData();
  }

  /**
   * GET /scenarios/itemTimings
   * Win rates for certain item timings on a hero for items that cost at least 1400 gold
   * @param item Filter by item name e.g. \&quot;spirit_vessel\&quot; (optional)
   * @param heroId Hero ID (optional)
   * @return ApiResponse&lt;List&lt;ScenarioItemTimingsResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<ScenarioItemTimingsResponse>> getScenariosItemTimingsWithHttpInfo(@jakarta.annotation.Nullable String item, @jakarta.annotation.Nullable Long heroId) throws ApiException {
    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "item", item)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "hero_id", heroId));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<ScenarioItemTimingsResponse>> localVarReturnType = new GenericType<List<ScenarioItemTimingsResponse>>() {};
    return apiClient.invokeAPI("ScenariosApi.getScenariosItemTimings", "/scenarios/itemTimings", "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /scenarios/laneRoles
   * Win rates for heroes in certain lane roles
   * @param laneRole Filter by lane role 1-4 (Safe, Mid, Off, Jungle) (optional)
   * @param heroId Hero ID (optional)
   * @return List&lt;ScenarioLaneRolesResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<ScenarioLaneRolesResponse> getScenariosLaneRoles(@jakarta.annotation.Nullable String laneRole, @jakarta.annotation.Nullable Long heroId) throws ApiException {
    return getScenariosLaneRolesWithHttpInfo(laneRole, heroId).getData();
  }

  /**
   * GET /scenarios/laneRoles
   * Win rates for heroes in certain lane roles
   * @param laneRole Filter by lane role 1-4 (Safe, Mid, Off, Jungle) (optional)
   * @param heroId Hero ID (optional)
   * @return ApiResponse&lt;List&lt;ScenarioLaneRolesResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<ScenarioLaneRolesResponse>> getScenariosLaneRolesWithHttpInfo(@jakarta.annotation.Nullable String laneRole, @jakarta.annotation.Nullable Long heroId) throws ApiException {
    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "lane_role", laneRole)
    );
    localVarQueryParams.addAll(apiClient.parameterToPairs("", "hero_id", heroId));

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<ScenarioLaneRolesResponse>> localVarReturnType = new GenericType<List<ScenarioLaneRolesResponse>>() {};
    return apiClient.invokeAPI("ScenariosApi.getScenariosLaneRoles", "/scenarios/laneRoles", "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
  /**
   * GET /scenarios/misc
   * Miscellaneous team scenarios
   * @param scenario Name of the scenario (see teamScenariosQueryParams) (optional)
   * @return List&lt;ScenarioMiscResponse&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public List<ScenarioMiscResponse> getScenariosMisc(@jakarta.annotation.Nullable String scenario) throws ApiException {
    return getScenariosMiscWithHttpInfo(scenario).getData();
  }

  /**
   * GET /scenarios/misc
   * Miscellaneous team scenarios
   * @param scenario Name of the scenario (see teamScenariosQueryParams) (optional)
   * @return ApiResponse&lt;List&lt;ScenarioMiscResponse&gt;&gt;
   * @throws ApiException if fails to make API call
   * @http.response.details
     <table border="1">
       <caption>Response Details</caption>
       <tr><td> Status Code </td><td> Description </td><td> Response Headers </td></tr>
       <tr><td> 200 </td><td> Success </td><td>  -  </td></tr>
     </table>
   */
  public ApiResponse<List<ScenarioMiscResponse>> getScenariosMiscWithHttpInfo(@jakarta.annotation.Nullable String scenario) throws ApiException {
    // Query parameters
    List<Pair> localVarQueryParams = new ArrayList<>(
            apiClient.parameterToPairs("", "scenario", scenario)
    );

    String localVarAccept = apiClient.selectHeaderAccept("application/json; charset=utf-8");
    String localVarContentType = apiClient.selectHeaderContentType();
    GenericType<List<ScenarioMiscResponse>> localVarReturnType = new GenericType<List<ScenarioMiscResponse>>() {};
    return apiClient.invokeAPI("ScenariosApi.getScenariosMisc", "/scenarios/misc", "GET", localVarQueryParams, null,
                               new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), localVarAccept, localVarContentType,
                               null, localVarReturnType, false);
  }
}
