package org.boundless.cf.servicebroker.model;

public class BoundlessAppResourceConstants {
	
	public static final String GEOSERVER_HOST = "GEOSERVER_HOST";
	
	public static final String GEOSERVER_ADMIN_ID = "GEOSERVER_ADMIN_USERNAME";
	public static final String GEOSERVER_ADMIN_PASSWD = "GEOSERVER_ADMIN_PASSWORD";
	
	public static final String GWC_ADMIN_ID = "GWC_ADMIN_USERNAME";
	public static final String GWC_ADMIN_PASSWD = "GWC_ADMIN_PASSWORD";

	public static final String GWC_TYPE = "geocache";
	public static final String GEOSERVER_TYPE = "geoserver";

	private static final String[] BOUNDLESS_APP_RESOURCE_TYPES = { GEOSERVER_TYPE, GWC_TYPE };

	public static String[] getTypes() {
		return BOUNDLESS_APP_RESOURCE_TYPES;
	}
	
	public static boolean isOfType(String key, String type) {
		return key.startsWith(type);
	}
};