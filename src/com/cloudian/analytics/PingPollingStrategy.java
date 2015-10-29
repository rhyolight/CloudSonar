package com.cloudian.analytics;

import java.net.InetAddress;

public class PingPollingStrategy extends PollingStrategy {

	public PingPollingStrategy(PollingUpdateHandler[] handlers) {
		super(handlers);
	}

	@Override
	PollingJob createPollingJob(PollingStrategy strategy, InetAddress host) {
		return new PingPollingJob(strategy, host);
	}

}
