package com.cloudian.analytics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PollingJobMonitor extends Thread {
	
	private static final Logger logger = LogManager.getLogger(PollingJobMonitor.class);
	
	private final PollingStrategy strategy;
	private final PollingJob job;
	private final long monitoringInterval;
	
	public PollingJobMonitor(PollingStrategy strategy, PollingJob job, long monitoringInterval) {
		this.strategy = strategy;
		this.job = job;
		this.monitoringInterval = monitoringInterval;
	}

	@Override
	public void run() {
		
		logger.debug("monitor got started for " + this.job.toString() + " at an interval of " + this.monitoringInterval);
		
		this.job.start();
		
		while(true) {
			
			try {
				Thread.sleep(monitoringInterval);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			// update status
			logger.trace("updating status of " + this.job.toString());
			if (this.strategy.updateStatus(this.job)) {
				break;
			}
			
		}
		
		logger.debug("monotor finished for " + this.job.toString());
		
	}

}
