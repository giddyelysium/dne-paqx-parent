package com.dell.cpsd.paqx.dne.repository;

import com.dell.cpsd.paqx.dne.domain.ComponentDetails;
import com.dell.cpsd.paqx.dne.domain.CredentialDetails;
import com.dell.cpsd.paqx.dne.domain.DneJob;
import com.dell.cpsd.paqx.dne.domain.EndpointDetails;
import com.dell.cpsd.paqx.dne.domain.scaleio.ScaleIOData;
import com.dell.cpsd.paqx.dne.domain.vcenter.Host;
import com.dell.cpsd.paqx.dne.domain.vcenter.PortGroup;
import com.dell.cpsd.paqx.dne.domain.vcenter.VCenter;
import com.dell.cpsd.paqx.dne.service.model.ComponentEndpointIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * TODO: Document Usage
 * <p>
 * Copyright &copy; 2017 Dell Inc. or its subsidiaries.  All Rights Reserved.
 * </p>
 *
 * @version 1.0
 * @since 1.0
 */
public class H2DataRepository implements DataServiceRepository
{
    private static final Logger LOG = LoggerFactory.getLogger(H2DataRepository.class);

    @PersistenceContext
    public EntityManager entityManager;

    @Override
    @Transactional
    public boolean saveScaleIoComponentDetails(final List<ComponentDetails> componentEndpointDetailsList)
    {
        LOG.info("Persisting ScaleIO Component, Endpoint and Credential UUID");

        if (componentEndpointDetailsList.isEmpty())
        {
            LOG.error("No Components Found");
            return false;
        }

        try
        {
            componentEndpointDetailsList.forEach(entityManager::persist);
            return true;
        }
        catch (Exception e)
        {
            LOG.error(" Exception occurred while persisting scaleio data", e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean saveVCenterComponentDetails(final List<ComponentDetails> componentEndpointDetailsList)
    {
        LOG.info("Persisting VCenter Component, Endpoint and Credential UUID");

        if (componentEndpointDetailsList.isEmpty())
        {
            LOG.error("No Components Found");
            return false;
        }

        try
        {
            componentEndpointDetailsList.forEach(entityManager::persist);
            return true;
        }
        catch (Exception e)
        {
            LOG.error(" Exception occurred while persisting vcenter data", e);
            return false;
        }
    }

    @Override
    public ComponentEndpointIds getComponentEndpointIds(final String componentType)
    {
        final TypedQuery<ComponentDetails> typedQuery = entityManager
                .createQuery("select ceids from ComponentDetails as ceids where ceids.componentType = :componentType",
                        ComponentDetails.class);
        typedQuery.setParameter("componentType", componentType);

        final List<ComponentDetails> componentDetailsList = typedQuery.getResultList();

        if (componentDetailsList != null && !componentDetailsList.isEmpty())
        {
            //For MVP, fetching the first component, 1st endpoint, 1st credential
            final ComponentDetails componentDetails = componentDetailsList.get(0);

            if (componentDetails == null)
            {
                return null;
            }

            final List<EndpointDetails> endpointDetailsList = componentDetails.getEndpointDetails();

            if (endpointDetailsList == null || endpointDetailsList.isEmpty())
            {
                return null;
            }

            final EndpointDetails endpointDetails = endpointDetailsList.get(0);

            if (endpointDetails == null)
            {
                return null;
            }

            final List<CredentialDetails> credentialDetailsList = endpointDetails.getCredentialDetailsList();

            if (credentialDetailsList == null || credentialDetailsList.isEmpty())
            {
                return null;
            }

            final CredentialDetails credentialDetails = credentialDetailsList.get(0);

            if (credentialDetails == null)
            {
                return null;
            }

            return new ComponentEndpointIds(componentDetails.getComponentUuid(), endpointDetails.getEndpointUuid(),
                    endpointDetails.getEndpointUrl(), credentialDetails.getCredentialUuid());

        }

        LOG.error("No Component Endpoints found in the database");

        return null;
    }

    @Override
    public ComponentEndpointIds getVCenterComponentEndpointIdsByEndpointType(final String endpointType)
    {
        final TypedQuery<ComponentDetails> typedQuery = entityManager.createQuery(
                "select endpoint.componentDetails from EndpointDetails as endpoint where endpoint.type = :endpointType and endpoint.componentDetails.componentType = :componentType",
                ComponentDetails.class);
        typedQuery.setParameter("endpointType", endpointType);
        typedQuery.setParameter("componentType", "VCENTER");

        try
        {
            final ComponentDetails componentDetails = typedQuery.getSingleResult();

            final List<EndpointDetails> endpointDetailsList = componentDetails.getEndpointDetails();

            if (endpointDetailsList == null || endpointDetailsList.isEmpty())
            {
                return null;
            }

            final EndpointDetails endpointDetails = endpointDetailsList.get(0);

            if (endpointDetails == null)
            {
                return null;
            }

            final List<CredentialDetails> credentialDetailsList = endpointDetails.getCredentialDetailsList();

            if (credentialDetailsList == null || credentialDetailsList.isEmpty())
            {
                return null;
            }

            final CredentialDetails credentialDetails = credentialDetailsList.get(0);

            if (credentialDetails == null)
            {
                return null;
            }

            return new ComponentEndpointIds(componentDetails.getComponentUuid(), endpointDetails.getEndpointUuid(),
                    endpointDetails.getEndpointUrl(), credentialDetails.getCredentialUuid());

        }
        catch (Exception e)
        {
            LOG.error("Exception occurred while fetching the component details", e);
            return null;
        }
    }

    @Override
    @Transactional
    public boolean saveVCenterData(final String jobId, final VCenter vCenterData)
    {
        TypedQuery<DneJob> query = entityManager.createQuery("select dne from DneJob as dne where dne.id = :jobId", DneJob.class);

        DneJob dneJob = null;

        try
        {
            dneJob = query.setParameter("jobId", jobId).getSingleResult();
        }
        catch (NoResultException e)
        {
            LOG.error("No job found with id: {}", jobId);
            LOG.error("No result", e.getMessage());
        }

        if (dneJob == null)
        {
            dneJob = new DneJob(jobId, null, vCenterData);
            entityManager.persist(dneJob);
        }
        else
        {
            LOG.info("DneJob != null for vcenter save");
            vCenterData.setJob(dneJob);
            dneJob.setVcenter(vCenterData);
            entityManager.merge(dneJob);
        }
        entityManager.flush();

        if (dneJob.getUuid() == null)
        {
            return false;
        }

        return true;
    }

    @Override
    @Transactional
    public boolean saveScaleIoData(final String jobId, final ScaleIOData scaleIOData)
    {
        TypedQuery<DneJob> query = entityManager.createQuery("select dne from DneJob as dne where dne.id = :jobId", DneJob.class);

        DneJob dneJob = null;

        try
        {
            dneJob = query.setParameter("jobId", jobId).getSingleResult();
        }
        catch (NoResultException e)
        {
            LOG.error("No job found with id: {}", jobId);
            LOG.error("No result", e.getMessage());
        }

        if (dneJob == null)
        {
            dneJob = new DneJob(jobId, scaleIOData, null);
            entityManager.persist(dneJob);
        }
        else
        {
            LOG.info("DneJob != null for scaleio save");
            scaleIOData.setJob(dneJob);
            dneJob.setScaleIO(scaleIOData);
            entityManager.merge(dneJob);
        }
        entityManager.flush();

        if (dneJob.getUuid() == null)
        {
            return false;
        }

        return true;
    }

    @Override
    public Host getVCenterHost(final String hostName) throws NoResultException
    {
        final TypedQuery<Host> query = entityManager.createQuery("SELECT h FROM Host as h where h.name=:hostName", Host.class);
        query.setParameter("hostName", hostName);
        return query.getSingleResult();
    }

    @Override
    public List<PortGroup> getPortGroups()
    {
        final TypedQuery<PortGroup> query = entityManager.createQuery("SELECT p FROM PortGroup as p", PortGroup.class);
        return query.getResultList();
    }

    @Override
    public ScaleIOData getScaleIoData(final String jobId)
    {
        final TypedQuery<ScaleIOData> query = entityManager
                .createQuery("SELECT dneJob.scaleIOData FROM DneJob as dneJob WHERE dneJob.id = :jobId", ScaleIOData.class);
        query.setParameter("jobId", jobId);

        final List<ScaleIOData> scaleIODataList = query.getResultList();

        if (scaleIODataList != null && !scaleIODataList.isEmpty())
        {
            return scaleIODataList.stream().findFirst().orElseGet(null);
        }

        return null;
    }
}