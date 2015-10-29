package com.cloudian.analytics;

import java.net.InetAddress;

/**
 * @See http://labs.gree.jp/blog/2010/06/336/
 * @See https://github.com/ebisawa/ruby-bulkping
 * @author tsato
 *
 */
public class PingPollingJob extends PollingJob {

	public PingPollingJob(PollingStrategy strategy, InetAddress host) {
		super(strategy, host);
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub

	}

}
