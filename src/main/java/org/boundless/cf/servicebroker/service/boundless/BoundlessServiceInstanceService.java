package org.boundless.cf.servicebroker.service.boundless;

import org.apache.log4j.Logger;
import org.boundless.cf.servicebroker.cfutils.CfAppManager;
import org.boundless.cf.servicebroker.cfutils.CfAppManager;
import org.boundless.cf.servicebroker.exception.ServiceBrokerException;
import org.boundless.cf.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.boundless.cf.servicebroker.exception.ServiceInstanceExistsException;
import org.boundless.cf.servicebroker.exception.ServiceInstanceUpdateNotSupportedException;
import org.boundless.cf.servicebroker.model.AppMetadataDTO;
import org.boundless.cf.servicebroker.model.BoundlessAppResourceType;
import org.boundless.cf.servicebroker.model.BoundlessServiceInstance;
import org.boundless.cf.servicebroker.model.BoundlessServiceInstanceMetadata;
import org.boundless.cf.servicebroker.model.CreateServiceInstanceRequest;
import org.boundless.cf.servicebroker.model.DeleteServiceInstanceRequest;
import org.boundless.cf.servicebroker.model.OperationState;
import org.boundless.cf.servicebroker.model.ServiceDefinition;
import org.boundless.cf.servicebroker.model.ServiceInstance;
import org.boundless.cf.servicebroker.model.ServiceInstanceLastOperation;
import org.boundless.cf.servicebroker.model.UpdateServiceInstanceRequest;
import org.boundless.cf.servicebroker.repository.BoundlessAppMetadataRepository;
import org.boundless.cf.servicebroker.repository.BoundlessServiceInstanceMetadataRepository;
import org.boundless.cf.servicebroker.repository.BoundlessServiceInstanceRepository;
import org.boundless.cf.servicebroker.repository.PlanRepository;
import org.boundless.cf.servicebroker.service.CatalogService;
import org.boundless.cf.servicebroker.service.ServiceInstanceService;
import org.cloudfoundry.client.CloudFoundryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.fn.tuple.Tuple2;

@Service
public class BoundlessServiceInstanceService implements ServiceInstanceService {

	private static final Logger log = Logger
			.getLogger(BoundlessServiceInstanceService.class);

	@Autowired
	CatalogService catalogService;

	@Autowired
	BoundlessServiceInstanceRepository serviceInstanceRepository;
	
	@Autowired
	BoundlessServiceInstanceMetadataRepository serviceInstanceMetadataRepository;
	
	@Autowired
	BoundlessAppMetadataRepository boundlessAppRepository;

	@Autowired
	PlanRepository planRepository;
	
	@Autowired
	CloudFoundryClient cfClient;

	@Override
	public ServiceInstance getServiceInstance(String id) {
		if (id == null)
			return null;
				
		BoundlessServiceInstance instance = getInstance(id);
		if (instance == null) {
			log.warn("Service instance with id: " + id + " not found!");
			return null;
		}
		
		// check the last operation
		ServiceInstanceLastOperation silo = instance
				.getLastOperation();
		
		String state = ((silo != null) ? silo.getState(): null);
		log.debug("service instance id: " + id + " is in state: " + state);

		// if this is a delete request and was successful, remove the instance
		if (instance.isCurrentOperationSuccessful()
				&& ServiceInstance.DELETE_REQUEST.equals(instance.getCurrentOperation())) {
			deleteInstance(instance);
		}

		// otherwise save the instance with the new last operation
		return saveInstance(instance);
	}

	@Override
	public ServiceInstance createServiceInstance(
			CreateServiceInstanceRequest request)
			throws ServiceInstanceExistsException, ServiceBrokerException {

		if (request == null 
				|| request.getServiceDefinitionId() == null 
				|| request.getPlanId() == null 
				|| request.getParameters() == null) {
			throw new ServiceBrokerException(
					"invalid CreateServiceInstanceRequest object.");
		}

		if (request.getServiceInstanceId() != null
				&& getInstance(request.getServiceInstanceId()) != null) {
			throw new ServiceInstanceExistsException(serviceInstanceRepository.findOne(request
					.getServiceInstanceId()));
		}

		ServiceDefinition sd = catalogService.getServiceDefinition(request
				.getServiceDefinitionId());

		if (sd == null) {
			throw new ServiceBrokerException(
					"Unable to find service definition with id: "
							+ request.getServiceDefinitionId());
		}

		BoundlessServiceInstance serviceInstance = new BoundlessServiceInstance(request);	
		serviceInstance.setCurrentOperation(ServiceInstance.CREATE_REQUEST);
		serviceInstance.setLastOperation(new ServiceInstanceLastOperation("Provisioning", OperationState.IN_PROGRESS));
    	serviceInstance = saveInstance(serviceInstance);
    	
    	createApp(serviceInstance);
    	serviceInstance = saveInstance(serviceInstance);

		log.info("Registered service instance: "
				+ serviceInstance);

		return serviceInstance;
	}

	@Override
	public ServiceInstance deleteServiceInstance(
			DeleteServiceInstanceRequest request) throws ServiceBrokerException {

		if (request == null || request.getServiceInstanceId() == null) {
			throw new ServiceBrokerException(
					"invalid DeleteServiceInstanceRequest object.");
		}

		BoundlessServiceInstance serviceInstance = getInstance(request.getServiceInstanceId());
		if (serviceInstance == null) {
			throw new ServiceBrokerException("Service instance: "
					+ request.getServiceInstanceId() + " not found.");
		}

		serviceInstance.setCurrentOperation(ServiceInstance.DELETE_REQUEST);
		serviceInstance.setLastOperation(new ServiceInstanceLastOperation("Deprovisioning", OperationState.IN_PROGRESS));
		serviceInstance = saveInstance(serviceInstance);
		log.info("deleting service instance: " + request.getServiceInstanceId() + ", full ServiceInstance details: " + serviceInstance);

		serviceInstance = deleteInstance(serviceInstance);

		log.info("Unregistered service instance: "
				+ serviceInstance);

		return serviceInstance;
	}

	@Override
	public ServiceInstance updateServiceInstance(
			UpdateServiceInstanceRequest request)
			throws ServiceInstanceUpdateNotSupportedException,
			ServiceBrokerException, ServiceInstanceDoesNotExistException {

		BoundlessServiceInstance existingInstance = getInstance(request.getServiceInstanceId());
		if (existingInstance == null || existingInstance.getId() == null) {
			return null;
		}
		log.info("Update on Service Instance: " + request);
		//BoundlessServiceInstance updateToInstance = new BoundlessServiceInstance(request);
		existingInstance.update(request);
		log.info("Updated service instance to: "
				+ existingInstance);
		
		// First persist the state so if any calls into check the state it can show as being deleted...
		existingInstance.setCurrentOperation(ServiceInstance.UPDATE_REQUEST);
		existingInstance.setLastOperation(new ServiceInstanceLastOperation("Updating", OperationState.IN_PROGRESS));
		existingInstance = saveInstance(existingInstance);	
		
		this.updateApp(existingInstance);
		existingInstance.getLastOperation().setState(OperationState.SUCCEEDED);
		
		BoundlessServiceInstanceMetadata boundlessSIMetadata = existingInstance.getMetadata();
		if (boundlessSIMetadata != null) {
			boundlessAppRepository.save(boundlessSIMetadata);	
		}

		existingInstance = saveInstance(existingInstance);
		log.info("Updated service instance: "
				+ existingInstance);
		return existingInstance;
	}

	private BoundlessServiceInstance getInstance(String id) {
		if (id == null)
			return null;
		
		return serviceInstanceRepository.findOne(id);
	}

	private BoundlessServiceInstance deleteInstance(BoundlessServiceInstance instance) {
		if (instance == null || instance.getId() == null) {
			return null;
		}
		
		// First persist the state so if any calls into check the state it can show as being deleted...
		instance.setCurrentOperation(BoundlessServiceInstance.DELETE_REQUEST);
		instance.setLastOperation(new ServiceInstanceLastOperation("Deprovisioning", 
										OperationState.IN_PROGRESS));
		serviceInstanceRepository.save(instance);
		
		log.info("Starting deletion of service instance: " + instance);
		
		this.deleteApp(instance);    	
		instance.getLastOperation().setState(OperationState.SUCCEEDED);
		saveInstance(instance);
		
		if (instance.isCurrentOperationSuccessful()
				&& instance.getCurrentOperation().equals(ServiceInstance.DELETE_REQUEST)) {
			
			BoundlessServiceInstanceMetadata boundlessSIMetadata = instance.getMetadata();			
			if (boundlessSIMetadata != null) {
				boundlessAppRepository.delete(boundlessSIMetadata);	
			}			
			serviceInstanceRepository.delete(instance.getId());
		}
		
		log.info("Deleted service instance: " + instance);
		return instance;
	}

	private BoundlessServiceInstance saveInstance(BoundlessServiceInstance instance) {
		return serviceInstanceRepository.save(instance);
	}	
	
	public void createApp(BoundlessServiceInstance serviceInstance) throws ServiceBrokerException {
		
		BoundlessServiceInstanceMetadata boundlessSIMetadata = serviceInstance.getMetadata();
		
		if (boundlessSIMetadata == null)
			return;
    	log.debug("Boundless App Metadata at create: " + boundlessSIMetadata);
    	
    	try {
    		String domain = boundlessSIMetadata.getDomain();
    		String org = boundlessSIMetadata.getOrg();
    		String space = boundlessSIMetadata.getSpace();
    		
    		// Initialize the Guids for Domain, Org & Space
    		boundlessSIMetadata.setDomainGuid(CfAppManager.requestDomainId(cfClient, domain).get());
	    	boundlessSIMetadata.setOrgGuid(CfAppManager.requestOrganizationId(cfClient, org).get());
	    	boundlessSIMetadata.setSpaceGuid(CfAppManager.requestSpaceId(cfClient, boundlessSIMetadata.getOrgGuid(), space).get());

    		String[] resourceTypes = BoundlessAppResourceType.getTypes(); 
	    	for(String resourceType: resourceTypes) {
		    	AppMetadataDTO appMetadata = boundlessSIMetadata.getAppMetadata(resourceType);
		    	if (appMetadata != null && appMetadata.getInstances() > 0) {
			    	Tuple2<String, String> resultPair = CfAppManager.push(cfClient, appMetadata).get(); 
			    	if (resultPair != null) {
			    		String appId = resultPair.t1;
			    		String routeId = resultPair.t2;
				    	boundlessSIMetadata.getResource(resourceType).setAppGuid(appId);
			    		boundlessSIMetadata.getResource(resourceType).setRouteGuid(routeId);			    		
			    	}
		    	}
	    	}
    	} catch(Exception e) {
    		e.printStackTrace();
    		// Cleanup all left-overs
    		this.deleteApp(serviceInstance);
    		throw new ServiceBrokerException("Error with service instance creation: " 
    								+ e.getMessage());
    	}
    	
    	serviceInstance.getLastOperation().setState(OperationState.SUCCEEDED);
    	serviceInstance.setMetadata(boundlessSIMetadata);
     	log.debug("Boundless App Metadata at end of push: " + boundlessSIMetadata);
	}

	public void updateApp(BoundlessServiceInstance serviceInstance) {
		
		BoundlessServiceInstanceMetadata boundlessSIMetadata = serviceInstance.getMetadata();
		
		if (boundlessSIMetadata == null)
			return;
		
     	log.debug("Boundless App Metadata at update: " + boundlessSIMetadata);
    	String[] resourceTypes = BoundlessAppResourceType.getTypes(); 
    	for(String resourceType: resourceTypes) {
	    	AppMetadataDTO appMetadata = boundlessSIMetadata.getAppMetadata(resourceType);
	    	if (appMetadata != null && appMetadata.getInstances() > 0) {
	    		CfAppManager.update(cfClient, appMetadata).get();
	    	}
    	}
    	
    	serviceInstance.getLastOperation().setState(OperationState.SUCCEEDED);
    	log.debug("Boundless App Metadata at end of update: " + boundlessSIMetadata);
    	serviceInstance.setMetadata(boundlessSIMetadata);
	}
	
	public void deleteApp(BoundlessServiceInstance serviceInstance) {
		
		BoundlessServiceInstanceMetadata boundlessSIMetadata = serviceInstance.getMetadata();
		if (boundlessSIMetadata == null)
			return;
		
		String[] resourceTypes = BoundlessAppResourceType.getTypes(); 
    	for(String resourceType: resourceTypes) {
	    	AppMetadataDTO appMetadata = boundlessSIMetadata.getAppMetadata(resourceType);
	    	if (appMetadata != null && appMetadata.getInstances() > 0) {
	    		// We might not have the complete app or route guid filled in case of error during app push,
	    		// Just clean up whatever is left over - do it separately for route & app
	    		CfAppManager.deleteRoute(cfClient, appMetadata.getRouteGuid()).get();
	    		CfAppManager.deleteApplications(cfClient, appMetadata.getSpaceGuid(), appMetadata.getName()).get();
	    	}
    	}
    	boundlessAppRepository.delete(boundlessSIMetadata);
    	serviceInstance.getLastOperation().setState(OperationState.SUCCEEDED);
	}
}