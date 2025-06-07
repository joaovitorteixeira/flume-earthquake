# Flume Earthquake

This project is a study case demonstrating how to create a custom Apache Flume Source using the  
[USGS Earthquake APIs](https://earthquake.usgs.gov/fdsnws/event/1/).  
It works by polling earthquake data at regular intervals by implementing
the [PollableSource Interface](https://flume.apache.org/releases/content/1.7.0/apidocs/org/apache/flume/PollableSource.html).

## Settings

The following settings are configurable:

- **dateStart**: The start date from which the source begins polling earthquake events. If not defined, it defaults to
  `01/01/2025`. The allowed format is using [ISO 8601](https://en.wikipedia.org/wiki/ISO_8601). Example: 2025-01-01T00:00:00Z 

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

