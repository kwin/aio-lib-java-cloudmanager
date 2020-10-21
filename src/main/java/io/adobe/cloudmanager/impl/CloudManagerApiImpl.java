package io.adobe.cloudmanager.impl;

import io.adobe.cloudmanager.CloudManagerApiException;
import io.adobe.cloudmanager.CloudManagerApi;
import io.adobe.cloudmanager.model.EmbeddedProgram;
import io.adobe.cloudmanager.model.Pipeline;
import io.adobe.cloudmanager.model.PipelineExecution;
import io.adobe.cloudmanager.swagger.invoker.ApiClient;
import io.adobe.cloudmanager.swagger.invoker.ApiException;
import io.adobe.cloudmanager.swagger.invoker.Pair;
import io.adobe.cloudmanager.swagger.model.PipelineList;
import io.adobe.cloudmanager.swagger.model.Program;
import io.adobe.cloudmanager.swagger.model.ProgramList;
import io.adobe.cloudmanager.util.Location;

import javax.ws.rs.core.GenericType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CloudManagerApiImpl implements CloudManagerApi {

  public CloudManagerApiImpl(String orgId, String apiKey, String accessToken) {
    this(orgId, apiKey, accessToken, null);
  }

  public CloudManagerApiImpl(String orgId, String apiKey, String accessToken, String baseUrl) {
    this.orgId = orgId;
    this.apiKey = apiKey;
    this.accessToken = accessToken;
    if (baseUrl != null) {
      this.baseUrl = baseUrl;
      apiClient.setBasePath(baseUrl);
    } else {
      this.baseUrl = apiClient.getBasePath();
    }
  }

  private final ApiClient apiClient = new ConfiguredApiClient();
  private final String orgId;
  private final String apiKey;
  private final String accessToken;
  private final String baseUrl;

  @Override
  public List<EmbeddedProgram> listPrograms() throws CloudManagerApiException {
    ProgramList programList = null;
    try {
      programList = get("/api/programs", new GenericType<ProgramList>() {});
    } catch (ApiException e) {
      throw new CloudManagerApiException(CloudManagerApiException.ErrorType.LIST_PROGRAMS, baseUrl, "/api/programs", e);
    }
    return programList.getEmbedded() == null ?
      Collections.emptyList() :
      programList.getEmbedded().getPrograms().stream().map(p -> new EmbeddedProgram(p, this)).collect(Collectors.toList());
  }

  @Override
  public List<Pipeline> listPipelines(String programId) throws CloudManagerApiException {
    return listPipelines(programId, p -> true);
  }

  @Override
  public List<Pipeline> listPipelines(String programId, Predicate<Pipeline> predicate) throws CloudManagerApiException {
    EmbeddedProgram embeddedProgram = listPrograms().stream().filter(p -> programId.equals(p.getId())).findFirst()
        .orElseThrow(() -> new CloudManagerApiException(CloudManagerApiException.ErrorType.FIND_PROGRAM, "8"));
    try {
      Program program = getProgram(embeddedProgram.getSelfLink());
      PipelineList pipelineList = get(program.getLinks().getHttpnsAdobeComadobecloudrelpipelines().getHref(), new GenericType<PipelineList>() {});
      return pipelineList.getEmbedded().getPipelines().stream().map(p -> new Pipeline(p, this)).filter(predicate).collect(Collectors.toList());
    } catch (ApiException e) {
      throw new CloudManagerApiException(CloudManagerApiException.ErrorType.LIST_PROGRAMS, baseUrl, "/api/programs", e);
    }
  }

  @Override
  public String startExecution(String programId, String pipelineId) throws CloudManagerApiException {
    Pipeline pipeline = getPipeline(programId, pipelineId);
    return startExecution(pipeline);
  }

  private Pipeline getPipeline(String programId, String pipelineId) throws CloudManagerApiException {
    return listPipelines(programId).stream().filter(p -> pipelineId.equals(p.getId())).findFirst()
        .orElseThrow(() -> new CloudManagerApiException(CloudManagerApiException.ErrorType.LIST_PROGRAMS, baseUrl, "/api/programs", null));
  }

  @Override
  public String startExecution(Pipeline pipeline) throws CloudManagerApiException {
    String executionHref = pipeline.getLinks().getHttpnsAdobeComadobecloudrelexecution().getHref();
    Location location = null;
    try {
      location = put(executionHref, new GenericType<Location>() {});
    } catch (ApiException e) {
      throw new CloudManagerApiException(CloudManagerApiException.ErrorType.LIST_PROGRAMS, baseUrl, "/api/programs", e);
    }
    return location.getRewrittenUrl(apiClient.getBasePath());
  }

  private Program getProgram(String path) throws CloudManagerApiException {
    try {
      return get(path, new GenericType<Program>() {
      });
    } catch (ApiException e) {
      throw new CloudManagerApiException(CloudManagerApiException.ErrorType.GET_PROGRAM, baseUrl, path, e);
    }
  }

  private <T> T get(String path, GenericType<T> returnType) throws ApiException {
    return doRequest(path, "GET", Collections.emptyList(), null, returnType);
  }

  private <T> T put(String path, GenericType<T> returnType) throws ApiException {
    return doRequest(path, "PUT", Collections.emptyList(), "", returnType);
  }

  private <T> T doRequest(String path, String method, List<Pair> queryParams, Object body, GenericType<T> returnType) throws ApiException {
    Map<String, String> headers = new HashMap<>();
    headers.put("x-gw-ims-org-id", orgId);
    headers.put("Authorization", String.format("Bearer %s", accessToken));
    headers.put("x-api-key", apiKey);

    return apiClient.invokeAPI(path, method, queryParams, body, headers, Collections.emptyMap(), "application/json", "application/json", new String[0], returnType);
  }
}
