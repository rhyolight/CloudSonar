package com.cloudian.analytics;

public class PollingStatus {
	
	public enum Status {
		NOT_STARTED, STARTED, FINISHED, ERROR
	};
	
	long started, stopped = -1;
	Status status = Status.NOT_STARTED;
	
	String error = "-";
	
	public void updateStatus(Status changed, String error) {
		switch (changed) {
		case STARTED:
			started = System.nanoTime();
			break;
		case FINISHED:
			stopped = System.nanoTime();
			break;
		case ERROR:
			stopped = System.nanoTime();
			if (error != null) {
				this.error = error;
			}
			break;
		};
		
		this.status = changed;
	}
	
	public boolean isStopped() {
		return this.stopped > -1;
	}
	
	public long duration() {
		if (!this.isStopped()) {
			return -1;
		}
		
		return this.stopped - this.started;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("STATUS: " + status.name());
		sb.append(", STARTED_AT: " + started);
		sb.append(", STOPPED_AT: " + stopped);
		sb.append(", DURATION: " + this.duration());
		sb.append(", ERROR: " + error);
		return sb.toString();
	}

}
