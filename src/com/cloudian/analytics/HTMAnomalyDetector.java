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
	static final String CLASSFIER_FIELD = "duration";
	
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
		String input = generateCSVInput(job);
		logger.debug("publishing a next input: " + input);
		htm.publisher.onNext(input);
		
	}
	
	private static String generateCSVInput(PollingJob job) {
		
		StringBuffer sb = new StringBuffer();
		
		sb.append(FULL_DATE_FORMAT.format(new Date()));
		sb.append(CSVUpdateHandler.DELIM);
		
		// ~ 10 micro sec = {0.0, 1.0}
		// ~ 100 micro sec = {1.0, 2.0}
		// ~ 1 milli sec = {2.0, 3.0}
		// ~ 10 milli sec = {3.0, 4.0}
		// ~ 100 milli sec = {4.0, 5.0}
		// ~ 1 sec = {5.0, 6.0}
		// ~ 10 sec = {6.0, 7.0}
		long micro = TimeUnit.MICROSECONDS.convert(job.pollingStatus.duration(), TimeUnit.NANOSECONDS);
		float log10 = Double.valueOf(Math.log10(micro)).floatValue();
		sb.append(log10);
		
		return sb.toString();
		
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
	                    //.add(new SpatialPooler())
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
        p.setParameterByKey(KEY.FIELD_ENCODING_MAP, fieldEncodings);
        
        return p;
	}
	
    public static Map<String, Map<String, Object>> getNetworkFieldEncodingMap() {
        Map<String, Map<String, Object>> fieldEncodings = setupMap(
                null,
                0, // n
                0, // w
                0, 0, 0, 0, null, null, null,
                "timestamp", "datetime", "DateEncoder");
        fieldEncodings = setupMap(
                fieldEncodings, 
                512, 
                21, 
                0, 7, 1, 0.1, null, Boolean.TRUE, null, 
                CLASSFIER_FIELD, "float", "ScalarEncoder");
        
        //fieldEncodings.get("timestamp").put(KEY.DATEFIELD_DOFW.getFieldName(), new Tuple(1, 1.0)); // Day of week
        fieldEncodings.get("timestamp").put(KEY.DATEFIELD_TOFD.getFieldName(), new Tuple(5, 4.0)); // Time of day
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
	
	public HTM(InetAddress address, Network network, Publisher publisher) {
		this.address = address;
		this.network = network;
		this.publisher = publisher;
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