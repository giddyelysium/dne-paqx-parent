package com.dell.cpsd.paqx.dne.service.task.handler.addnode;

import com.dell.cpsd.paqx.dne.domain.IWorkflowTaskHandler;
import com.dell.cpsd.paqx.dne.domain.Job;
import com.dell.cpsd.paqx.dne.repository.DataServiceRepository;
import com.dell.cpsd.paqx.dne.service.NodeService;
import com.dell.cpsd.paqx.dne.service.model.AddHostToVCenterResponse;
import com.dell.cpsd.paqx.dne.service.model.ComponentEndpointIds;
import com.dell.cpsd.paqx.dne.service.model.InstallEsxiTaskResponse;
import com.dell.cpsd.paqx.dne.service.model.Status;
import com.dell.cpsd.paqx.dne.service.task.handler.BaseTaskHandler;
import com.dell.cpsd.virtualization.capabilities.api.ClusterOperationRequest;
import com.dell.cpsd.virtualization.capabilities.api.ClusterOperationRequestMessage;
import com.dell.cpsd.virtualization.capabilities.api.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: Document Usage
 * <p/>
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries. All Rights Reserved. Dell EMC Confidential/Proprietary Information
 * <p/>
 *
 * @version 1.0
 * @since 1.0
 */
public class AddHostToVCenterTaskHandler extends BaseTaskHandler implements IWorkflowTaskHandler
{
    /**
     * The logger instance
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(AddHostToVCenterTaskHandler.class);

    /**
     * The <code>NodeService</code> instance
     */
    private final NodeService           nodeService;
    private final DataServiceRepository repository;

    public AddHostToVCenterTaskHandler(final NodeService nodeService, final DataServiceRepository repository)
    {
        this.nodeService = nodeService;
        this.repository = repository;
    }

    @Override
    public boolean executeTask(final Job job)
    {
        LOGGER.info("Execute Add Host to VCenter task");

        final AddHostToVCenterResponse response = initializeResponse(job);

        try
        {
            final ComponentEndpointIds componentEndpointIds = repository.getVCenterComponentEndpointIdsByEndpointType("VCENTER-CUSTOMER");

            if (componentEndpointIds == null)
            {
                throw new IllegalStateException("No VCenter components found.");
            }

            final InstallEsxiTaskResponse installEsxiTaskResponse = (InstallEsxiTaskResponse) job.getTaskResponseMap().get("installEsxi");

            if (installEsxiTaskResponse == null)
            {
                throw new IllegalStateException("No Install ESXi task response found");
            }

            final String hostname = installEsxiTaskResponse.getHostname();

            if (hostname == null)
            {
                throw new IllegalStateException("Host name is null");
            }

            final ClusterOperationRequestMessage requestMessage = new ClusterOperationRequestMessage();

            requestMessage.setCredentials(new Credentials(componentEndpointIds.getEndpointUrl(), null, null));
            requestMessage.setComponentEndpointIds(
                    new com.dell.cpsd.virtualization.capabilities.api.ComponentEndpointIds(componentEndpointIds.getComponentUuid(),
                            componentEndpointIds.getEndpointUuid(), componentEndpointIds.getCredentialUuid()));
            final ClusterOperationRequest clusterOperationRequest = new ClusterOperationRequest();
            clusterOperationRequest.setHostName(hostname);
            clusterOperationRequest.setClusterOperation(ClusterOperationRequest.ClusterOperation.ADD_HOST);
            requestMessage.setClusterOperationRequest(clusterOperationRequest);
            //TODO: Update vcenter adapter side for DNE to fetch cluster id

            final boolean success = this.nodeService.requestAddHostToVCenter(requestMessage);

            response.setWorkFlowTaskStatus(success ? Status.SUCCEEDED : Status.FAILED);

            return success;
        }
        catch (Exception e)
        {
            LOGGER.error("Exception occurred", e);
            response.addError(e.toString());
            return false;
        }
    }

    @Override
    public AddHostToVCenterResponse initializeResponse(Job job)
    {
        final AddHostToVCenterResponse response = new AddHostToVCenterResponse();
        response.setWorkFlowTaskName(job.getCurrentTask().getTaskName());
        response.setWorkFlowTaskStatus(Status.IN_PROGRESS);
        job.addTaskResponse(job.getStep(), response);

        return response;
    }

}