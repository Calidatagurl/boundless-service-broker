package org.boundless.cf.servicebroker.repository;

import org.boundless.cf.servicebroker.servicebroker.model.AppMetadata;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

@Component
public interface CFDockerAppRepository extends
		CrudRepository<AppMetadata, String> {

}
