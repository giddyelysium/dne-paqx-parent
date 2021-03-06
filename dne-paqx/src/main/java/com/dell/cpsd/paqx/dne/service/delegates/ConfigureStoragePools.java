/**
 * <p>
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries. All Rights Reserved. Dell EMC Confidential/Proprietary Information
 * </p>
 */

package com.dell.cpsd.paqx.dne.service.delegates;

import com.dell.cpsd.paqx.dne.domain.scaleio.ScaleIOStoragePool;
import com.dell.cpsd.paqx.dne.exception.TaskResponseFailureException;
import com.dell.cpsd.paqx.dne.service.NodeService;
import com.dell.cpsd.paqx.dne.service.delegates.model.NodeDetail;
import com.dell.cpsd.paqx.dne.service.model.ComponentEndpointIds;
import com.dell.cpsd.storage.capabilities.api.CreateStoragePoolRequestMessage;
import com.dell.cpsd.storage.capabilities.api.StoragePoolSpec;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.dell.cpsd.paqx.dne.service.delegates.utils.DelegateConstants.NODE_DETAILS;
import static com.dell.cpsd.paqx.dne.service.delegates.utils.DelegateConstants.CONFIGURE_STORAGE_POOLS_FAILED;

/**
 * Task responsible for creating new storage pools
 * <p>
 * <p>
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries. All Rights Reserved. Dell EMC Confidential/Proprietary Information
 * </p>
 *
 * @since 1.0
 */
@Component
@Scope("prototype")
@Qualifier("configureStoragePools")
public class ConfigureStoragePools extends BaseWorkflowDelegate
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigureStoragePools.class);

    private NodeService nodeService;

    /**
     * ScaleIO gateway credential components
     */
    private static final String COMPONENT_TYPE = "SCALEIO-CLUSTER";

    @Autowired
    public ConfigureStoragePools(final NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    @Override
    public void delegateExecute(final DelegateExecution delegateExecution)
    {
        LOGGER.info("In ConfigureStoragePools");
        List<NodeDetail> nodeDetails = (List<NodeDetail>) delegateExecution.getVariable(NODE_DETAILS);
        if (CollectionUtils.isEmpty(nodeDetails))
        {
            final String message = "The List of Node Detail was not found!  Please add at least one Node Detail and try again.";
            LOGGER.error(message);
            updateDelegateStatus(message);
            throw new BpmnError(CONFIGURE_STORAGE_POOLS_FAILED, message);
        }

        try
        {
            Map<String, Set<String>> protectionDomainToStoragePool = new HashMap<>();
            // Create a mapping of protection domain to their dummy storage pools
            nodeDetails.stream().filter(Objects::nonNull).forEach(nodeDetail -> {
                if (protectionDomainToStoragePool.get(nodeDetail.getProtectionDomainId()) == null && MapUtils
                        .isNotEmpty(nodeDetail.getDeviceToDeviceStoragePool()))
                {
                    protectionDomainToStoragePool.put(nodeDetail.getProtectionDomainId(), new HashSet<String>());
                }
                nodeDetail.getDeviceToDeviceStoragePool().values().stream().filter(device -> device.getStoragePoolId() == null)
                        .forEach(value -> {
                            protectionDomainToStoragePool.get(nodeDetail.getProtectionDomainId()).add(value.getStoragePoolName());
                        });
            });

            // For each protection domain, create storage pool
            if (MapUtils.isNotEmpty(protectionDomainToStoragePool))
            {
                protectionDomainToStoragePool.entrySet().stream().filter(entry -> !CollectionUtils.isEmpty(entry.getValue()))
                        .forEach(entry -> {
                            createValidStoragePool(entry.getKey(), entry.getValue());
                        });
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Error configuring valid storage pool(s)", e);
            updateDelegateStatus("Error configuring valid storage pool(s) " + e.getMessage());
            throw new BpmnError(CONFIGURE_STORAGE_POOLS_FAILED, e.getMessage());
        }

        LOGGER.info("Done configuring storage pool(s).");
    }

    /**
     * Creates a valid storage pool through scaleio adapter and saves the same to H2 database
     *
     * @param protectionDomainId Protection domain id for which to create the storage pool
     */
    private void createValidStoragePool(final String protectionDomainId, Set<String> storagePoolNames)
    {
        final ComponentEndpointIds componentEndpointIds = nodeService.getComponentEndpointIds(COMPONENT_TYPE);

        if (componentEndpointIds == null)
        {
            throw new IllegalStateException("No component ids found.");
        }

        storagePoolNames.stream().forEach(storagePoolName -> {
            String newStoragePool = createScaleIOStoragePool(protectionDomainId, componentEndpointIds, storagePoolName);

            // Sync up the same storage pool into H2 db
            ScaleIOStoragePool newlyCreatedStoragePool = nodeService.createStoragePool(storagePoolName, newStoragePool, protectionDomainId);
            LOGGER.info("Successfully saved new storage pool to database, " + newlyCreatedStoragePool.toString());
        });
    }

    private String createScaleIOStoragePool(final String protectionDomainId, final ComponentEndpointIds componentEndpointIds,
            final String storagePoolName)
    {
        CreateStoragePoolRequestMessage requestMessage = new CreateStoragePoolRequestMessage();
        requestMessage.setEndpointUrl("https://" + componentEndpointIds.getEndpointUrl());
        requestMessage.setComponentEndpointIds(
                new com.dell.cpsd.storage.capabilities.api.ComponentEndpointIds(componentEndpointIds.getComponentUuid(),
                        componentEndpointIds.getEndpointUuid(), componentEndpointIds.getCredentialUuid()));

        StoragePoolSpec storagePoolSpec = new StoragePoolSpec();
        storagePoolSpec.setProtectionDomainId(protectionDomainId);
        storagePoolSpec.setRmCacheWriteHandlingMode(StoragePoolSpec.RmCacheWriteHandlingMode.PASSTHROUGH);
        storagePoolSpec.setStoragePoolName(storagePoolName);
        storagePoolSpec.setUseRmcache(false);
        storagePoolSpec.setZeroPaddingEnabled(true);
        requestMessage.setStoragePoolSpec(storagePoolSpec);

        String newStoragePool = null;
        try
        {
            newStoragePool = nodeService.createStoragePool(requestMessage);
        }
        catch (TaskResponseFailureException e)
        {
            LOGGER.error("Failed to create storage pool", e);
            updateDelegateStatus("Failed to create storage pool: " + e.getMessage());
        }
        if (newStoragePool == null || newStoragePool.length() == 0)
        {
            throw new IllegalStateException("Create storage pool request failed");
        }
        return newStoragePool;
    }
}
