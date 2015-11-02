package com.cloudian.analytics;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class PollingStrategy {
	
	private static final Logger logger = LogManager.getLogger(PollingStrategy.class);
	
	private Map<InetAddress, PollingJob> pollingJobMap = new HashMap<InetAddress, PollingJob>(); 
	
	private final PollingUpdateHandler[] handlers;
	
	public PollingStrategy(PollingUpdateHandler[] handlers) {
		this.handlers = handlers;
	}
	
	/**
	 * This method should not block, nor throw an exception.
	 * If the previous polling is not yet stopped, then should return without doing anything.
	 * @param host
	 * @param handler
	 */
	void poll(InetAddress host) {
		
		logger.debug("polling " + host.getHostName());
		
		if (pollingJobMap.get(host) != null) {
			logger.debug("attemted to poll, but skipped " + host.getHostName());
			return;
		}
		
		PollingJob job = this.createPollingJob(this, host);
		pollingJobMap.put(host, job);
		job.start();
		
	};
	
	abstract PollingJob createPollingJob(PollingStrategy strategy, InetAddress host);
	
	void updateStatus(PollingJob job) {
		
		logger.debug("updating status: " + job);
		
		if (job.pollingStatus.isStopped()) {
			// remove from the map
			this.pollingJobMap.remove(job.host);
		}
		
		if (this.handlers == null) {
			return;
		}
		
		// call handler
		for (PollingUpdateHandler handler : this.handlers) {
			handler.updateStatus(job);
		}
		
	}

}
