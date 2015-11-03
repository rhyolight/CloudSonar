/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudian.analytics;

import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cloudian.analytics.PollingStatus.Status;

public class FailureDetectorUpdateHandler implements PollingUpdateHandler {
	
	private static final Logger logger = LogManager.getLogger(FailureDetectorUpdateHandler.class);
	private static final int SAMPLE_SIZE = 1000;
	private static final DecimalFormat format = new DecimalFormat("#.##");
	
	private final Map<InetAddress, ArrivalWindow> arrivalSamples = new Hashtable<InetAddress, ArrivalWindow>();
	
	public FailureDetectorUpdateHandler() {
		// default constructor	
	}
	
	public long getMean(InetAddress address) {
		
		ArrivalWindow window = this.arrivalSamples.get(address);
		
		if (window == null) {
			return 0;
		}
		
		long meanInNano = Double.valueOf(window.mean()).longValue();
		return TimeUnit.MILLISECONDS.convert(meanInNano, TimeUnit.NANOSECONDS);
		
	}

	@Override
	public void updateStatus(PollingJob job) {
		
		if (job.pollingStatus.status.equals(Status.ERROR)) {
			// do nothing for now
			return;
		}
		
		ArrivalWindow heartbeatWindow = this.arrivalSamples.get(job.host);
		if (heartbeatWindow == null) {
			
			heartbeatWindow = new ArrivalWindow(SAMPLE_SIZE);
            heartbeatWindow.add(job.pollingStatus.duration());
            arrivalSamples.put(job.host, heartbeatWindow);
			
		} else {
			
			double phi = 0.0;
			long duration = 0;
			
			if (!job.pollingStatus.isStopped()) {
				// compute phi
				duration = System.nanoTime() - job.pollingStatus.started;
				phi = ArrivalWindow.PHI_FACTOR * heartbeatWindow.phi(duration);
			} else {
				// compute phi
				duration = job.pollingStatus.duration();
				phi = ArrivalWindow.PHI_FACTOR * heartbeatWindow.phi(duration);
				// done
				heartbeatWindow.add(job.pollingStatus.duration());
			}
			
			StringBuffer sb = new StringBuffer();
			sb.append(job.host.getHostName());
			sb.append(CSVUpdateHandler.DELIM);
			sb.append(job.pollingStatus.status);
			sb.append(CSVUpdateHandler.DELIM);
			sb.append(duration);
			sb.append(CSVUpdateHandler.DELIM);
			sb.append(this.format.format(phi));
			logger.info(sb.toString());
			
		}
		
	}

}
class ArrivalWindow
{
	private static final Logger logger = LogManager.getLogger(ArrivalWindow.class);
    private final BoundedStatsDeque arrivalIntervals;

    // this is useless except to provide backwards compatibility in phi_convict_threshold,
    // because everyone seems pretty accustomed to the default of 8, and users who have
    // already tuned their phi_convict_threshold for their own environments won't need to
    // change.
    static final double PHI_FACTOR = 1.0 / Math.log(10.0);

    // in the event of a long partition, never record an interval longer than the rpc timeout,
    // since if a host is regularly experiencing connectivity problems lasting this long we'd
    // rather mark it down quickly instead of adapting
    // this value defaults to the same initial value the FD is seeded with
    static final long MAX_INTERVAL_IN_NANO = ServerHealthChecker.POLLING_INTERVAL_IN_SECONDS * 1000 * 1000 * 1000 * 2;

    ArrivalWindow(int size)
    {
        arrivalIntervals = new BoundedStatsDeque(size);
    }

    synchronized void add(long value)
    {
        arrivalIntervals.add(value);
    }

    double mean()
    {
        return arrivalIntervals.mean();
    }

    // see CASSANDRA-2597 for an explanation of the math at work here.
    double phi(long current)
    {
        assert arrivalIntervals.size() > 0; // should not be called before any samples arrive
        return current / mean();
    }
}

class BoundedStatsDeque implements Iterable<Long>
{
    private final LinkedBlockingDeque<Long> deque;
    private final AtomicLong sum;

    public BoundedStatsDeque(int size)
    {
        deque = new LinkedBlockingDeque<>(size);
        sum = new AtomicLong(0);
    }

    public Iterator<Long> iterator()
    {
        return deque.iterator();
    }

    public int size()
    {
        return deque.size();
    }

    public void add(long i)
    {
        if (!deque.offer(i))
        {
            Long removed = deque.remove();
            sum.addAndGet(-removed);
            deque.offer(i);
        }
        sum.addAndGet(i);
    }

    public long sum()
    {
        return sum.get();
    }

    public double mean()
    {
        return size() > 0 ? ((double) sum()) / size() : 0;
    }
}