package com.cloudian.analytics;

import java.net.InetAddress;

import com.cloudian.analytics.PollingStatus.Status;

public abstract class PollingJob extends Thread{
	
	final PollingStatus status = new PollingStatus();
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
		this.status.updateStatus(Status.STARTED, null);
		
		// notify
		this.strategy.updateStatus(this);
		
	}
	
	protected void finished() {
		
		// update status object
		this.status.updateStatus(Status.FINISHED, null);
		
		// notify
		this.strategy.updateStatus(this);
		
	}
	
	protected void failed(String error) {
		
		// update status object
		this.status.updateStatus(Status.ERROR, error);
		
		// notify
		this.strategy.updateStatus(this);
		
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("jobTarget: " + this.host.getHostName());
		sb.append(", ");
		sb.append(this.status.toString());
		return sb.toString();
	}

}
