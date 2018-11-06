package com.hubspot.baragon.service.resources;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.hubspot.baragon.auth.NoAuth;
import com.hubspot.baragon.data.BaragonStateDatastore;
import com.hubspot.baragon.data.BaragonAliasDatastore;
import com.hubspot.baragon.models.BaragonRequest;
import com.hubspot.baragon.models.BaragonResponse;
import com.hubspot.baragon.models.BaragonService;
import com.hubspot.baragon.models.QueuedRequestId;
import com.hubspot.baragon.models.UpstreamInfo;
import com.hubspot.baragon.service.managers.RequestManager;
import com.hubspot.baragon.service.worker.BaragonRequestWorker;

@Path("/request")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public class RequestResource {
  private static final Logger LOG = LoggerFactory.getLogger(RequestResource.class);

  private final BaragonStateDatastore stateDatastore;
  private final RequestManager manager;
  private final ObjectMapper objectMapper;
  private final BaragonAliasDatastore aliasDatastore;

  @Inject
  public RequestResource(BaragonStateDatastore stateDatastore, RequestManager manager, ObjectMapper objectMapper, BaragonAliasDatastore aliasDatastore) {
    this.stateDatastore = stateDatastore;
    this.manager = manager;
    this.objectMapper = objectMapper;
    this.aliasDatastore = aliasDatastore;
  }

  @GET
  @NoAuth
  @Path("/{requestId}")
  public Optional<BaragonResponse> getResponse(@PathParam("requestId") String requestId) {
    return manager.getResponse(requestId);
  }

  @POST
  public BaragonResponse enqueueRequest(@Valid BaragonRequest request) {
    try {
      BaragonRequest updatedForAliases = aliasDatastore.updateForAliases(request);
      LOG.info(String.format("Received request: %s", objectMapper.writeValueAsString(request)));
      return manager.enqueueRequest(updatedForAliases);
    } catch (Exception e) {
      LOG.error(String.format("Caught exception for %s", request.getLoadBalancerRequestId()), e);
      return BaragonResponse.failure(request.getLoadBalancerRequestId(), e.getMessage());
    }
  }

  @GET
  @NoAuth
  public List<QueuedRequestId> getQueuedRequestIds() {
    return manager.getQueuedRequestIds();
  }

  @GET
  @NoAuth
  @Path("/history/{serviceId}")
  public List<BaragonResponse> getRecentRequestIds(@PathParam("serviceId") String serviceId) {
    return manager.getResponsesForService(serviceId);
  }

  @DELETE
  @Path("/{requestId}")
  public BaragonResponse cancelRequest(@PathParam("requestId") String requestId) {
    // prevent race conditions when transitioning from a cancel-able to not cancel-able state
    synchronized (BaragonRequestWorker.class) {
      manager.cancelRequest(requestId);
      return manager.getResponse(requestId).or(BaragonResponse.requestDoesNotExist(requestId));
    }
  }

  @PUT
  @Path("/upstreams/{serviceId}")
  public BaragonResponse addUpstream(@PathParam("serviceId") String serviceId, @QueryParam("upstream") String upstream) {
    UpstreamInfo upstreamInfo = UpstreamInfo.fromString(upstream);
    Optional<BaragonService> maybeService = stateDatastore.getService(serviceId);
    if (!maybeService.isPresent()) {
      throw new WebApplicationException(String.format("Service %s not found", serviceId), 400);
    }
    return enqueueRequest(new BaragonRequest(
        UUID.randomUUID().toString(),
        maybeService.get(),
        Collections.singletonList(upstreamInfo),
        Collections.emptyList(),
        Collections.emptyList(),
        Optional.absent(),
        Optional.absent(),
        true,
        false,
        true
    ));
  }

  @POST
  @Path("/upstreams/{serviceId}")
  public BaragonResponse setUpstreams(@PathParam("serviceId") String serviceId, List<UpstreamInfo> upstreams) {
    Optional<BaragonService> maybeService = stateDatastore.getService(serviceId);
    if (!maybeService.isPresent()) {
      throw new WebApplicationException(String.format("Service %s not found", serviceId), 400);
    }
    return enqueueRequest(new BaragonRequest(
        UUID.randomUUID().toString(),
        maybeService.get(),
        Collections.emptyList(),
        Collections.emptyList(),
        upstreams,
        Optional.absent(),
        Optional.absent(),
        true,
        false,
        true
    ));
  }

  @DELETE
  @Path("/upstreams/{serviceId}")
  public BaragonResponse removeUpstream(@PathParam("serviceId") String serviceId, @QueryParam("upstream") String upstream) {
    UpstreamInfo upstreamInfo = UpstreamInfo.fromString(upstream);
    Optional<BaragonService> maybeService = stateDatastore.getService(serviceId);
    if (!maybeService.isPresent()) {
      throw new WebApplicationException(String.format("Service %s not found", serviceId), 400);
    }
    return enqueueRequest(new BaragonRequest(
        UUID.randomUUID().toString(),
        maybeService.get(),
        Collections.emptyList(),
        Collections.singletonList(upstreamInfo),
        Collections.emptyList(),
        Optional.absent(),
        Optional.absent(),
        true,
        false,
        true
    ));
  }
}
