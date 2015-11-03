package com.cloudian.analytics;

import java.net.InetAddress;

import com.cloudian.analytics.PollingStatus.Status;

public abstract class PollingJob extends Thread{
	
	final PollingStatus pollingStatus = new PollingStatus();
	final PollingStrategy strategy;
	final InetAddress host;
	
	/*
	 * disable to call the default constructor
	 */
	private PollingJob() {
		this.strategy = null;
		this.host = null;
	};
	
	public PollingJob(PollingStrategy strategy, InetAddress host) {
		// TODO: Set a meaningfull thread name here
		this.strategy = strategy;
		this.host = host;
	}

	/**
	 * Once started, and when its status gets changed,
	 * it has to notify via started, finished, and failed.
	 */
	@Override
	public abstract void run();
	
	protected void started() {
		
		// update status object
		this.pollingStatus.updateStatus(Status.STARTED, null);
		
	}
	
	protected void finished() {
		
		// update status object
		this.pollingStatus.updateStatus(Status.FINISHED, null);
		
	}
	
	protected void failed(String error) {
		
		// update status object
		this.pollingStatus.updateStatus(Status.ERROR, error);
		
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("jobTarget: " + this.host.getHostName());
		sb.append(", ");
		sb.append(this.pollingStatus.toString());
		return sb.toString();
	}

}
