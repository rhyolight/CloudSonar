package com.cloudian.analytics;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server Health Checker keeps polling a given set of hosts,
 * and record their response times to detect their health statuses 
 * by analyzing their subtle differences.
 * 
 * A polling method(e.g. PING, HTTP HEAD) should be pluggable.
 * 
 * Also, the detection design is based on the following paper.
 * "The φ Accrual Failure Detector"
 * http://www.jaist.ac.jp/~defago/files/pdf/IS_RR_2004_010.pdf
 * 
 * @author tsato
 *
 */
public class ServerHealthChecker {
	
	private static final Logger logger = LogManager.getLogger(ServerHealthChecker.class);
	
	private static final long POLLING_INTERVAL_IN_SECONDS = 1;
	
	private final String[] hosts;
	private PollingStrategy pollingStrategy;
	
	private ServerHealthChecker(String[] hosts) {
		
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
		return new PollingUpdateHandler[]{new CSVUpdateHandler()};
	}
	
	private void start(InetAddress[] addresses) {
		
		logger.debug("polling " + addresses.length + " hosts");
		
		for (InetAddress address : addresses) {
			
			this.pollingStrategy.poll(address);
			
		}
		
	}

	public static void main(String[] args) {
		
		final ServerHealthChecker checker = new ServerHealthChecker(args);
		
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
