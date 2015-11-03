package com.cloudian.analytics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cloudian.analytics.PollingStatus.Status;

public class CSVUpdateHandler implements PollingUpdateHandler {
	
	private static final Logger logger = LogManager.getLogger(CSVUpdateHandler.class);
	static final String DELIM = ", ";
	
	public CSVUpdateHandler() {
		// default constructor
	}

	@Override
	public void updateStatus(PollingJob job) {
		
		if (!job.pollingStatus.isStopped()) {
			return;
		}
		
		StringBuffer sb = new StringBuffer(job.host.getHostName());
		sb.append(DELIM);
		
		if (job.pollingStatus.status.equals(Status.FINISHED)) {
			sb.append(job.pollingStatus.duration());
		} else {
			// error
			sb.append(job.pollingStatus.error);
		}
		
		logger.info(sb.toString());
		
	}

}
