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
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cloudian.analytics.PollingStatus.Status;

public class FailureDetectorUpdateHandler implements PollingUpdateHandler {
	
	private static final Logger logger = LogManager.getLogger(FailureDetectorUpdateHandler.class);
	
	private static final int SAMPLE_SIZE = 1000;
	
	private final Map<InetAddress, ArrivalWindow> arrivalSamples = new Hashtable<InetAddress, ArrivalWindow>();
	
	private static final ScheduledExecutorService failureDetectionService = Executors.newSingleThreadScheduledExecutor();
	
	private static final DecimalFormat format = new DecimalFormat("#.###");
	
	public FailureDetectorUpdateHandler() {
		// default constructor
		
		failureDetectionService.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				
				for (InetAddress address : arrivalSamples.keySet()) {
					
					ArrivalWindow window = arrivalSamples.get(address);
					
					if (window == null) {
						return;
					}
					
					// compute phi
					long tnow = System.nanoTime();
					long current = tnow - window.tLast;
					double phi = ArrivalWindow.PHI_FACTOR * window.phi(tnow);
					
					// output
					StringBuffer sb = new StringBuffer();
					sb.append(address.getHostName());
					sb.append(CSVUpdateHandler.DELIM);
					sb.append(current);
					sb.append(CSVUpdateHandler.DELIM);
					sb.append(format.format(phi));
					logger.info(sb.toString());
					
				}
				
			}
			
		}, 2, 1, TimeUnit.SECONDS);
		
	}

	@Override
	public void updateStatus(PollingJob job) {
		
		if (!job.pollingStatus.isStopped() || job.pollingStatus.status.equals(Status.ERROR)) {
			return;
		}
		
		long now = System.nanoTime();
		ArrivalWindow heartbeatWindow = this.arrivalSamples.get(job.host);
		if (heartbeatWindow == null) {
			
			heartbeatWindow = new ArrivalWindow(SAMPLE_SIZE);
            heartbeatWindow.add(now);
            arrivalSamples.put(job.host, heartbeatWindow);
			
		} else {
			
			heartbeatWindow.add(now);
			
		}
		
	}

}
class ArrivalWindow
{
	private static final Logger logger = LogManager.getLogger(ArrivalWindow.class);
    long tLast = 0L;
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
        assert tLast >= 0;
        if (tLast > 0L)
        {
            long interArrivalTime = (value - tLast);
            /*
            if (interArrivalTime <= MAX_INTERVAL_IN_NANO)
                arrivalIntervals.add(interArrivalTime);
            else
                logger.debug("Ignoring interval time of {}", interArrivalTime);
                */
            arrivalIntervals.add(interArrivalTime);
        }
        else
        {
            // We use a very large initial interval since the "right" average depends on the cluster size
            // and it's better to err high (false negatives, which will be corrected by waiting a bit longer)
            // than low (false positives, which cause "flapping").
            arrivalIntervals.add(MAX_INTERVAL_IN_NANO);
        }
        tLast = value;
    }

    double mean()
    {
        return arrivalIntervals.mean();
    }

    // see CASSANDRA-2597 for an explanation of the math at work here.
    double phi(long tnow)
    {
        assert arrivalIntervals.size() > 0 && tLast > 0; // should not be called before any samples arrive
        long t = tnow - tLast;
        return t / mean();
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