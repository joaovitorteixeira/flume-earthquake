# Flume Earthquake

This project is a study case demonstrating how to create a custom Apache Flume Source using the  
[USGS Earthquake APIs](https://earthquake.usgs.gov/fdsnws/event/1/).  
It works by polling earthquake data at regular intervals by implementing
the [PollableSource Interface](https://flume.apache.org/releases/content/1.7.0/apidocs/org/apache/flume/PollableSource.html).

## Settings

The following settings are configurable:

- **dateStart**: A fixed start date ([ISO 8601](https://en.wikipedia.org/wiki/ISO_8601)) to begin fetching historical data.
  The system will fetch data from this date up to the current time, and once it reaches "now", 
it switches to using pollingWindowMs to continue polling new and potentially late data.
<br>Default: 01/01/2025
<br>Example: 2025-01-01T00:00:00Z

- **poolingWindowMs**: The duration (in milliseconds) of the time window used for polling recent data once the system 
catches up to the present.
<br>This setting ensures the system captures late-arriving data by repeatedly fetching records from the last 
`pollingWindowMs` milliseconds. 
<br>Default: 86400000 (24 hours).

## Usage

To build the project, execute the following command

```bash
mvn clean package
```

### Configuration example

This is an example of how you can configure the earthquake source using `KafkaSink`.

```
EarthquakeAgent.sources=Earthquake
EarthquakeAgent.channels=MemChannel
EarthquakeAgent.sinks=kafkaSink

EarthquakeAgent.sources.Earthquake.type=org.jvt.EarthquakeSource
EarthquakeAgent.sources.Earthquake.dateStart=2025-06-01T00:00:00Z

EarthquakeAgent.sinks.kafkaSink.type=org.apache.flume.sink.kafka.KafkaSink
EarthquakeAgent.sinks.kafkaSink.kafka.bootstrap.servers=localhost:9092
EarthquakeAgent.sinks.kafkaSink.kafka.topic=earthquake-events
EarthquakeAgent.sinks.kafkaSink.kafka.flumeBatchSize=20

#EarthquakeAgent.channels.MemChannel.capacity=10000
#EarthquakeAgent.channels.MemChannel=transactionCapacity=100
EarthquakeAgent.channels.MemChannel.type=file

EarthquakeAgent.sources.Earthquake.channels=MemChannel
EarthquakeAgent.sinks.kafkaSink.channel=MemChannel
```

