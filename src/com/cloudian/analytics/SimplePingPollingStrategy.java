package com.cloudian.analytics;

import java.net.InetAddress;

public class SimplePingPollingStrategy extends PollingStrategy {

	public SimplePingPollingStrategy(PollingUpdateHandler[] handlers) {
		super(handlers);
	}

	@Override
	PollingJob createPollingJob(InetAddress host) {
		return new SimplePingPollingJob(host);
	}

}
