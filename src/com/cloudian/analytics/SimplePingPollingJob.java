package com.cloudian.analytics;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is a simple PING job with InetAddress#isReacheable.
 * 
 * # ping cloudian-node1
 * PING cloudian-node1 (10.100.204.225) 56(84) bytes of data.
 * 64 bytes from cloudian-node1 (10.100.204.225): icmp_seq=1 ttl=64 time=0.100 ms
 * 64 bytes from cloudian-node1 (10.100.204.225): icmp_seq=2 ttl=64 time=0.128 ms
 * 64 bytes from cloudian-node1 (10.100.204.225): icmp_seq=3 ttl=64 time=0.134 m
 * 64 bytes from cloudian-node1 (10.100.204.225): icmp_seq=4 ttl=64 time=0.142 ms
 * 
 * @author tsato
 *
 */
public class SimplePingPollingJob extends PollingJob {
	
	private static final Logger logger = LogManager.getLogger(SimplePingPollingJob.class);
	
	private static final int TIMEOUT = 10000;

	public SimplePingPollingJob(PollingStrategy strategy, InetAddress host) {
		super(strategy, host);
	}

	@Override
	public void run() {
		
		logger.debug("PingPolling to " + this.host.getHostName() + " is about to start");
		
		this.started();
		
		logger.debug("PingPolling to " + this.host.getHostName() + " started");
		
		try {
			
			if (!this.host.isReachable(TIMEOUT)) {
				
				this.failed("unreacheable");
				
				logger.debug("PingPolling to " + this.host.getHostName() + " failed");
				
				return;
				
			}
			
		} catch (IOException e) {
			
			this.failed(e.getMessage());
			
			logger.debug("PingPolling to " + this.host.getHostName() + " failed");
			
			return;
			
		}
		
		this.finished();
		
		logger.debug("PingPolling to " + this.host.getHostName() + " finished");
		
	}

}
