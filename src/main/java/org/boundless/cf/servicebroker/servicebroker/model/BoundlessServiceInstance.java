package org.boundless.cf.servicebroker.servicebroker.model;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Entity(name="boundless_service_instances")
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE)
public class BoundlessServiceInstance extends ServiceInstance {

	// In Boundless, the service instance is actually tied to an app pushed to CF
	@JsonSerialize
	@JsonProperty("app_metadata")
	@OneToOne(optional = true, orphanRemoval = true, fetch=FetchType.LAZY, cascade=CascadeType.ALL)
	private AppMetadata appMetadata; 

	// any "metadata" key-value pairs    
	//@ElementCollection(fetch = FetchType.LAZY)
	//@MapKeyColumn(name="name")
    //@Column(name="value")
    //@CollectionTable(name="metadata", joinColumns=@JoinColumn(name="metadata_id"))
	//protected Map<String,String> metadata = new HashMap<String,String>();

	public BoundlessServiceInstance() {  
		super(); 
	}
	
	public BoundlessServiceInstance(CreateServiceInstanceRequest request) {
		super(request);
		this.setAppMetadata(request.getParameters());
		log.info("App Metadata now set to" + this.appMetadata);
	}

	public BoundlessServiceInstance(DeleteServiceInstanceRequest request) {
		super(request);
	}

	public BoundlessServiceInstance(UpdateServiceInstanceRequest request) {
		super(request);
		this.setAppMetadata(request.getParameters());
		log.info("App Metadata now set to" + this.appMetadata);
	}
	
	/*
	public static BoundlessServiceInstance create(CreateServiceInstanceRequest request) {
		BoundlessServiceInstance instance = new BoundlessServiceInstance(request);
		instance.withAsync(true);
		instance.getMetadata().put(CREATE_REQUEST_ID, instance.getId() );

		return instance;
	}

	public static BoundlessServiceInstance update(BoundlessServiceInstance instance,
			OperationState state) {
		ServiceInstanceLastOperation silo = new ServiceInstanceLastOperation(
				instance.getLastOperation().getDescription(),
				state);
		instance.withLastOperation(silo);
		return instance;
	}

	public static BoundlessServiceInstance delete(BoundlessServiceInstance instance,
			String deleteRequestId) {
		instance.getMetadata().put(DELETE_REQUEST_ID, deleteRequestId);
		ServiceInstanceLastOperation silo = new ServiceInstanceLastOperation(
				deleteRequestId, OperationState.IN_PROGRESS);
		instance.withLastOperation(silo);

		return instance;
	}
	*/
	
	public void update(BoundlessServiceInstance updateTo) {
		super.update(updateTo);
		AppMetadata updateToAppMetadata = updateTo.getAppMetadata();
		this.appMetadata.update(updateToAppMetadata);
		log.info("App Metadata now set to" + this.appMetadata);
	}
	
	@Override
	public String toString() {
		return "BoundlessServiceInstance [" + super.toString() + ", appMetadata=" + appMetadata
				+ ", parameters=" + parameters + "]";
	}

	public AppMetadata getAppMetadata() {
		return appMetadata;
	}

	public void setAppMetadata(AppMetadata appMetadata) {
		this.appMetadata = appMetadata;
	}
	
	private void setAppMetadata(Map<String, Object> appMetadataMap) {
		if (appMetadataMap == null) {
			return;
		}
		
		this.appMetadata = new AppMetadata();
		appMetadata.generateAndSetId();
		
		for(String key: appMetadataMap.keySet()) {
			Object val = appMetadataMap.get(key);
			log.info("Setting key: " + key + " with value: " + val);
			this.appMetadata.setMapping(key, val);
			
			log.info("App Metadata : " + appMetadata);
		}
	}

}
