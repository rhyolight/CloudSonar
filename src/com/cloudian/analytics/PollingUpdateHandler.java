package com.cloudian.analytics;

public interface PollingUpdateHandler {
	
	/**
	 * This is called when a major status change occured
	 * @param status
	 */
	void updateStatus(PollingJob status);

}
