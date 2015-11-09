# CloudSonar

CloudSonar is a tool to monitor your server healths in your private cloud to help you detect an over loaded server before it produces an error.

## Problem

This tool is the one you look for if the following problems apply to you:

* monitoring items are too many or too specific
* monitoring items are always insufficient
* a heavy big data analytics sometimes triggers errors because of a poor resource management
* your loadbalancer is not intelligent enough to avoid too much load on your particular server
* fixed timeouts complicate your server configurations, especially when they are across multiple datacenters or when new and old machines are mixed

## Concept

A PING response time contains the following information:

1. a load on a sender server
2. a network congestion
3. a load on a recipient server
4. any other noises

So, if a PING response time is recorded at a fixed interval, by carefully analyzing those records, it could extract valuable information that represents **a node to node server health**.

One such proven method is [Phi Failure Detector](http://www.jaist.ac.jp/~defago/files/pdf/IS_RR_2004_010.pdf). It has been used to detect a node down by [Apache Cassandra](https://github.com/apache/cassandra).
With Phi Failure Detector, you can get a suspicion level called PHI score. For example, if a suspicion level is 1, that means there is a chance by 10% that a current waiting time or longer could happen.

In the paper, or in Cassandra, such a suspicion level is not used for load balancing. But I assume it also represents a server health(the higher, the more load/the slower response).

Yet, simply lowering a suspicion level doesn't really help because it's not hard to imagine you get so many false alarms. For example, there is a pattern in any IT service like a peak around midnight.
And a temporal high load could occur during such a peak period. But, if a certain high load has been proved as OK, then, such a load should be considered normal.

I think [HTM](https://en.wikipedia.org/wiki/Hierarchical_temporal_memory) is the best for such a temporal pattern recognition/prediction. Actually, HTM Challenge by [NuPIC](http://numenta.org) inspired me to try this.

So, let HTM produce an anomaly score for a current wait time, then, when the following conditions are met, a node to node server health is considered **needs-attention**:

1. HTM raises an anomaly score higher than a given threshold
2. PHI score is higher than a given threshold

The condition 1 means an unexpected pattern was observed. And the condition 2 tells if it is a bad sign or a good sign.

## Usage

There is no installation required, but you need Java 8 to run this tool. If your server wide Java environment is not Java 8, then you can pass CLOUDSONAR_JAVA_HOME environmental variable.
Then, simply upload the contents in a *build* folder to your prefereed location, and run the following commands:

```
e.g. 
cloudian-node6 => cloudian-node1, cloudian-node2
JAVA 8 is at /root/jdk1.8.0_65

[root@cloudian-node6 build]# export CLOUDSONAR_JAVA_HOME=/root/jdk1.8.0_65
[root@cloudian-node6 build]# ./run.sh cloudian-node1 cloudian-node2
```

The tool will produces the following logs in *logs* folder:

* sonar.csv (timestamp, host name, response time in nano seconds)
* fd.csv    (timestamp, host name, response time in nano seconds, status, PHI score)
* htm.csv   (timestamp, host name, log10 of response time in micro seconds, prediction, anomaly score)

## Technical Notes

### PING implementation

In Linux, PING uses a raw socket that is allowed only for a privileged user. In Java, no such a raw socket is available. But a convinient method is available as [InetAddress#isReacheable](https://docs.oracle.com/javase/8/docs/api/java/net/InetAddress.html#isReachable-int-). The JavaDoc says:

> A typical implementation will use ICMP ECHO REQUESTs if the privilege can be obtained, otherwise it will try to establish a TCP connection on port 7 (Echo) of the destination host.

Even if I write my own ICMP ECHO implementatoin in C, this privilege issue is not resolved. So I decided to use [InetAddress#isReacheable](https://docs.oracle.com/javase/8/docs/api/java/net/InetAddress.html#isReachable-int-).

### Estimating the distribution of PING response times

This is a sample distribution of PING response times I tried on my S3 object storage cluster. x is a response time in 100 micro seconds. y is the number of responses. 

cloudian-node1 received all the S3 traffic generated on cloudian-node2. Also, I dropped ICMP puckets several times on cloudian-node2. They were shown on the longer latencies.

But in overal, you can see the distribution is exponential.

![](https://github.com/ggsato/CloudSonar/blob/master/resources/images/duration_distribution.PNG)

In the original paper, the distribution of inter-arrival times was estimated as a normal distribution. While in Cassandra, it is implemented as an exponential one. So I borrowed the implementation of Cassandra, and modified a little for it to accept a response time instead of an arrival time.

Note that CloudSonar measures a response time, but both of the original paper and Cassandra did an inter arrival time.

### PHI and response time

Here's a sample of PHI values and response times. The Y on the left is PHI, and response time in 100 micro seconds is on the right. Around 22:30, response times got longer, and PHI showed large values by responding to the changes. A response time after that remained high, but Phi Failure Detector dropped as it has persisted.

![](https://github.com/ggsato/CloudSonar/blob/master/resources/images/phi_and_duration.PNG)

This is the distribution of PHI values. Most values are below 1.0, but spread wide.

![](https://github.com/ggsato/CloudSonar/blob/master/resources/images/phi_distribution.PNG)

### Response time format(HTM input)

The format of response time is log10(response time in micro seconds). If a raw value of response time is directly used, a noise in a larger value has more significance. 

response time | log10
-------------:|-----:
1 µ | 0
10 µs | 1
100 µs | 2
1 ms | 3
10 ms | 4
100 ms | 5
1 s | 6
10 s | 7

Thus, a practical range of log10 is from 2 to 7.
