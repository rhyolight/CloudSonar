package com.cloudian.analytics;

import java.net.InetAddress;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.numenta.nupic.Parameters;
import org.numenta.nupic.Parameters.KEY;
import org.numenta.nupic.algorithms.Anomaly;
import org.numenta.nupic.algorithms.SpatialPooler;
import org.numenta.nupic.algorithms.TemporalMemory;
import org.numenta.nupic.encoders.Encoder;
import org.numenta.nupic.network.Inference;
import org.numenta.nupic.network.Network;
import org.numenta.nupic.network.sensor.ObservableSensor;
import org.numenta.nupic.network.sensor.Publisher;
import org.numenta.nupic.network.sensor.Sensor;
import org.numenta.nupic.network.sensor.SensorParams;
import org.numenta.nupic.network.sensor.SensorParams.Keys;
import org.numenta.nupic.util.Tuple;

import com.cloudian.analytics.PollingStatus.Status;

import rx.Subscriber;

public class HTMAnomalyDetector implements PollingUpdateHandler {
	
	static final Logger logger = LogManager.getLogger(HTMAnomalyDetector.class);
	private static final String FULL_DATE = "YYYY/MM/dd HH:mm:ss";
	static final DateFormat FULL_DATE_FORMAT = new SimpleDateFormat(FULL_DATE);
	static final DecimalFormat LOG_FORMAT = new DecimalFormat("#.###");
	static final String CLASSFIER_FIELD = "log10_resp";
	static final int NUMBER_OF_AGGREGATIONS = 30;
	
	private final Map<InetAddress, HTM> htmMaps = new HashMap<InetAddress, HTM>();
	
	public void HTMAnomalyDetector() {
		// default constructor
	}

	@Override
	public void updateStatus(PollingJob job) {
		
		if (!job.pollingStatus.status.equals(Status.FINISHED)) {
			// do nothing for now
			return;
		}
		
		HTM htm = this.htmMaps.get(job.host);
		if (htm == null) {
			logger.debug("starting a new network for " + job.host);
			
			// create
			Publisher publisher = Publisher.builder()
										.addHeader("timestamp" + CSVUpdateHandler.DELIM + CLASSFIER_FIELD)
										.addHeader("datetime" + CSVUpdateHandler.DELIM + "float")
										.addHeader("T" + CSVUpdateHandler.DELIM)
										.build();
			
			Object[] n = { "publisher", publisher};
	        SensorParams parms = SensorParams.create(Keys::obs, n);
	        Sensor<ObservableSensor<String>> sensor = Sensor.create(ObservableSensor::create, parms);
	        Network network = createNetwork(sensor);
	        
	        // create htm
	        htm = new HTM(job.host, network, publisher);
	        this.htmMaps.put(job.host, htm);
	        // add observer
	        network.observe().subscribe(htm);
	        // start
	        network.start();
			
		}
		
		// add
		logger.debug("pushing job: " + job.toString());
		htm.push(job);
		
	}
	
	private static Network createNetwork(Sensor<ObservableSensor<String>> sensor) {
		
		Parameters p = buildParams();
		p = p.union(buildEncoderParams());
		
		return Network.create("CloudSonar", p)
	            .add(Network.createRegion("Region")
	                .add(Network.createLayer("Layer", p)
	                    .alterParameter(KEY.AUTO_CLASSIFY, Boolean.TRUE)
	                    .add(Anomaly.create())
	                    .add(new TemporalMemory())
	                    .add(new SpatialPooler())
	                    .add(sensor)
	                    )
	                );
	}
	
	private static Parameters buildParams() {
		return Parameters.getAllDefaultParameters();
	}
	
	private static Parameters buildEncoderParams() {
		
		Map<String, Map<String, Object>> fieldEncodings = getNetworkFieldEncodingMap();
        Parameters p = Parameters.getEncoderDefaultParameters();
        
        // CLAClassifier
        // alpha, hard coded
        
        // Spatial Pooler
        // ? 'columnCount': 2048,
        p.setParameterByKey(KEY.GLOBAL_INHIBITIONS, true); // 'globalInhibition': 1,
        // ? 'inputWidth': 0,
        p.setParameterByKey(KEY.MAX_BOOST, 2.0); // 'maxBoost': 2.0,
        p.setParameterByKey(KEY.NUM_ACTIVE_COLUMNS_PER_INH_AREA, 40.0); // 'numActiveColumnsPerInhArea': 40,
        p.setParameterByKey(KEY.POTENTIAL_PCT, 0.8); // 'potentialPct': 0.8,
        // ? 'seed': 1956,
        // 'spVerbosity': 0,
        // ? 'spatialImp': 'cpp',
        p.setParameterByKey(KEY.SYN_PERM_ACTIVE_INC, 0.05); // 'synPermActiveInc': 0.05,
        // 'synPermConnected': 0.1,
        p.setParameterByKey(KEY.SYN_PERM_INACTIVE_DEC, 0.04216241137734589);// 'synPermInactiveDec': 0.04216241137734589
        
        // Temporal Memory Pooler
        p.setParameterByKey(KEY.ACTIVATION_THRESHOLD, 14); // 'activationThreshold': 14,
        // 'cellsPerColumn': 32,
        // 'columnCount': 2048,
        // ? 'globalDecay': 0.0,
        // 'initialPerm': 0.21,
        // ? 'inputWidth': 2048,
        // ? 'maxAge': 0,
        // ? 'maxSegmentsPerCell': 128,
        // ? 'maxSynapsesPerSegment': 32,
        p.setParameterByKey(KEY.MIN_THRESHOLD, 11); // 'minThreshold': 11,
        // 'newSynapseCount': 20,
        // ? 'outputType': 'normal',
        // ? 'pamLength': 3,
        // 'permanenceDec': 0.1,
        // 'permanenceInc': 0.1,
        // ? 'seed': 1960,
        // ? 'temporalImp': 'cpp',
        
        p.setParameterByKey(KEY.FIELD_ENCODING_MAP, fieldEncodings);
        
        return p;
	}
	
	/**
	 * This configuration is based on various swarming results.
	 * The sample input file, and its swarming results are in resources/sample folder.
	 * @return
	 */
    public static Map<String, Map<String, Object>> getNetworkFieldEncodingMap() {
        Map<String, Map<String, Object>> fieldEncodings = setupMap(
                null,
                0, // n
                0, // w
                0, 0, 0, 0, null, null, null,
                "timestamp", "datetime", "DateEncoder");
        fieldEncodings = setupMap(
                fieldEncodings, 
                41, 
                21, 
                2.0, 7.0, 1.0, 0.1, null, Boolean.TRUE, null, 
                // AdaptiveScalarEncoder is suggested by swarming,
                // but which is not available for this version of htm.java
                CLASSFIER_FIELD, "float", "ScalarEncoder");
        
        fieldEncodings.get("timestamp").put(KEY.DATEFIELD_DOFW.getFieldName(), new Tuple(21, 3.514898609719035)); // Day of week
        fieldEncodings.get("timestamp").put(KEY.DATEFIELD_PATTERN.getFieldName(), FULL_DATE);
        
        return fieldEncodings;
    }
    
    /**
     * Sets up an Encoder Mapping of configurable values.
     *  
     * @param map               if called more than once to set up encoders for more
     *                          than one field, this should be the map itself returned
     *                          from the first call to {@code #setupMap(Map, int, int, double, 
     *                          double, double, double, Boolean, Boolean, Boolean, String, String, String)}
     * @param n                 the total number of bits in the output encoding
     * @param w                 the number of bits to use in the representation
     * @param min               the minimum value (if known i.e. for the ScalarEncoder)
     * @param max               the maximum value (if known i.e. for the ScalarEncoder)
     * @param radius            see {@link Encoder}
     * @param resolution        see {@link Encoder}
     * @param periodic          such as hours of the day or days of the week, which repeat in cycles
     * @param clip              whether the outliers should be clipped to the min and max values
     * @param forced            use the implied or explicitly stated ratio of w to n bits rather than the "suggested" number
     * @param fieldName         the name of the encoded field
     * @param fieldType         the data type of the field
     * @param encoderType       the Camel case class name minus the .class suffix
     * @return
     * 
     * @See org.numenta.nupic.examples.napi.hotgym.NetworkDemoHarness
     */
    public static Map<String, Map<String, Object>> setupMap(
            Map<String, Map<String, Object>> map,
            int n, int w, double min, double max, double radius, double resolution, Boolean periodic,
            Boolean clip, Boolean forced, String fieldName, String fieldType, String encoderType) {

        if(map == null) {
            map = new HashMap<String, Map<String, Object>>();
        }
        Map<String, Object> inner = null;
        if((inner = map.get(fieldName)) == null) {
            map.put(fieldName, inner = new HashMap<String, Object>());
        }

        inner.put("n", n);
        inner.put("w", w);
        inner.put("minVal", min);
        inner.put("maxVal", max);
        inner.put("radius", radius);
        inner.put("resolution", resolution);

        if(periodic != null) inner.put("periodic", periodic);
        if(clip != null) inner.put("clipInput", clip);
        if(forced != null) inner.put("forced", forced);
        if(fieldName != null) inner.put("fieldName", fieldName);
        if(fieldType != null) inner.put("fieldType", fieldType);
        if(encoderType != null) inner.put("encoderType", encoderType);

        return map;
    }

}

class HTM extends Subscriber<Inference>{
	
	final InetAddress address;
	final Network network;
	final Publisher publisher;
	private long maxDuration = 0;
	private int passed = 0;
	
	public HTM(InetAddress address, Network network, Publisher publisher) {
		this.address = address;
		this.network = network;
		this.publisher = publisher;
	}
	
	void push(PollingJob job) {
		
		long duration = job.pollingStatus.duration();
		
		if (this.passed == 0) {
			
			// new
			this.maxDuration = duration;
			this.passed++;
			
		} else if (this.maxDuration < duration){
			
			// lost
			this.maxDuration = duration;
			this.passed++;
			
		} else {
			
			// won
			this.passed++;
			
			if (this.passed == HTMAnomalyDetector.NUMBER_OF_AGGREGATIONS) {
				
				this.publish();
				this.maxDuration = 0;
				this.passed = 0;
				
			}
			
		}
		
		HTMAnomalyDetector.logger.debug("a job was pushed, the current maxDuration, won = " + this.maxDuration + ", " + this.passed);
		
	}
	
	private void publish() {
		
		StringBuffer sb = new StringBuffer();
		
		sb.append(HTMAnomalyDetector.FULL_DATE_FORMAT.format(new Date()));
		sb.append(CSVUpdateHandler.DELIM);
		
		// ~ 10 micro sec = {0.0, 1.0}
		// ~ 100 micro sec = {1.0, 2.0}
		// ~ 1 milli sec = {2.0, 3.0}
		// ~ 10 milli sec = {3.0, 4.0}
		// ~ 100 milli sec = {4.0, 5.0}
		// ~ 1 sec = {5.0, 6.0}
		// ~ 10 sec = {6.0, 7.0}
		long micro = TimeUnit.MICROSECONDS.convert(this.maxDuration, TimeUnit.NANOSECONDS);
		float log10 = Double.valueOf(Math.log10(micro)).floatValue();
		sb.append(log10);
		
		String input = sb.toString();
		HTMAnomalyDetector.logger.debug("publishing a next input: " + input);
		publisher.onNext(input);
		
	}

	@Override
	public void onCompleted() {
		HTMAnomalyDetector.logger.debug("HTM Network completed");
	}

	@Override
	public void onError(Throwable error) {
		HTMAnomalyDetector.logger.error("HTM Network threw an error!", error);
	}

	@Override
	public void onNext(Inference infer) {
		
        StringBuilder sb = new StringBuilder();
        sb.append(address.getHostName());
        sb.append(CSVUpdateHandler.DELIM);
        sb.append(infer.getRecordNum());
        sb.append(CSVUpdateHandler.DELIM);
        sb.append(HTMAnomalyDetector.LOG_FORMAT.format(infer.getClassifierInput().get(HTMAnomalyDetector.CLASSFIER_FIELD).get("inputValue")));
        sb.append(CSVUpdateHandler.DELIM);
        Object pred = infer.getClassification(HTMAnomalyDetector.CLASSFIER_FIELD).getMostProbableValue(1);
        sb.append(pred == null ? "N/A" : HTMAnomalyDetector.LOG_FORMAT.format(pred));
        sb.append(CSVUpdateHandler.DELIM);
        sb.append(infer.getAnomalyScore());
        
        HTMAnomalyDetector.logger.info(sb.toString());
	}
}