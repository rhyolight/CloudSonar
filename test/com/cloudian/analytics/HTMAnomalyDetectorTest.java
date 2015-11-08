package com.cloudian.analytics;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;

public class HTMAnomalyDetectorTest {
	
	@Test
	public void testSimple() throws UnknownHostException {
		
		HTMAnomalyDetector detector = new HTMAnomalyDetector();
		InetAddress local = InetAddress.getLocalHost();
		
		for (int i=0; i<1000; i++) {
			PollingJob job = new SimplePingPollingJob(local);
			job.start();
			
			while(!job.pollingStatus.isStopped()) {
				try {
					// local PING is within 1ms
					Thread.sleep(1);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			detector.updateStatus(job);
		}
		
	}

}
