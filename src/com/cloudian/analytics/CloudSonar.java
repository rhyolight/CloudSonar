package com.cloudian.analytics;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CloudSonar is a tool that you can use as a sonar in a cloud.
 * This tool allows you to keep polling a given set of hosts,
 * and record their response times to analyze their subtle changes over time.
 * 
 * A polling method(e.g. PING, HTTP HEAD) and update handlers are pluggable.
 * 
 * SimplePingPollingStrategy is available as a default polling method.
 * 
 * The following two handlers are available by default.
 * 1. CSVUpdateHandler
 * 2. FailureDetectorUpdateHandler
 * 
 * @author tsato
 *
 */
public class CloudSonar {
	
	private static final Logger logger = LogManager.getLogger(CloudSonar.class);
	
	static final long POLLING_INTERVAL_IN_SECONDS = 1;
	
	private final String[] hosts;
	private PollingStrategy pollingStrategy;
	
	private CloudSonar(String[] hosts) {
		
		this.hosts = hosts;
		
	}
	
	private InetAddress[] configure() throws UnknownHostException {
		
		//construct PollingStrategy
		this.pollingStrategy = new SimplePingPollingStrategy(this.createHandlers());
		
		// resolve hosts
		InetAddress[] addresses = new InetAddress[this.hosts.length];
		for (int i = 0; i<this.hosts.length; i++) {
			addresses[i] = InetAddress.getByName(hosts[i]);
		}
		
		return addresses;
	}
	
	private PollingUpdateHandler[] createHandlers() {
		return new PollingUpdateHandler[]{new CSVUpdateHandler(), new FailureDetectorUpdateHandler(), new HTMAnomalyDetector()};
	}
	
	private void start(InetAddress[] addresses) {
		
		logger.debug("polling " + addresses.length + " hosts");
		
		for (InetAddress address : addresses) {
			
			this.pollingStrategy.poll(address);
			
		}
		
	}

	public static void main(String[] args) {
		
		final CloudSonar checker = new CloudSonar(args);
		
		InetAddress[] addresses = null;
		try {
			addresses = checker.configure();
		} catch (UnknownHostException e) {
			logger.error("Some host names provided are not resolved.");
			e.printStackTrace();
			System.exit(1);
		}
		
		while (true) {
			
			checker.start(addresses);
			
			try {
				Thread.sleep(POLLING_INTERVAL_IN_SECONDS * 1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
	}

}
