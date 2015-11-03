package com.cloudian.analytics;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class PollingStrategy {
	
	private static final Logger logger = LogManager.getLogger(PollingStrategy.class);
	private static final long MINIMUM_MONITORING_INTERVAL = 100;
	
	final Map<InetAddress, PollingJob> pollingJobMap = new HashMap<InetAddress, PollingJob>(); 
	
	private PollingUpdateHandler[] handlers;
	private final FailureDetectorUpdateHandler fdUpdateHandler;
	
	public PollingStrategy(PollingUpdateHandler[] handlers) {
		this.handlers = handlers;
		
		
		if (this.handlers != null) {
			for (PollingUpdateHandler handler : this.handlers) {
				
				if (handler instanceof FailureDetectorUpdateHandler) {
					this.fdUpdateHandler = (FailureDetectorUpdateHandler) handler;
					return;
				}
				
			}
		}
		this.fdUpdateHandler = null;
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
		
		PollingJobMonitor monitor = new PollingJobMonitor(this, job, Math.max(MINIMUM_MONITORING_INTERVAL, this.fdUpdateHandler.getMean(host)));
		monitor.start();
		
	};
	
	abstract PollingJob createPollingJob(PollingStrategy strategy, InetAddress host);
	
	boolean updateStatus(PollingJob job) {
		
		logger.debug("updating status: " + job);
		
		boolean stopped = false;
		
		if (job.pollingStatus.isStopped()) {
			// remove from the map
			this.pollingJobMap.remove(job.host);
			stopped = true;
		}
		
		if (this.handlers == null) {
			return stopped;
		}
		
		// call handler
		for (PollingUpdateHandler handler : this.handlers) {
			handler.updateStatus(job);
		}
		
		return stopped;
		
	}

}