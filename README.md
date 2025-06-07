# Flume Earthquake

This project is a study case demonstrating how to create a custom Apache Flume Source using the  
[USGS Earthquake APIs](https://earthquake.usgs.gov/fdsnws/event/1/).  
It works by polling earthquake data at regular intervals by implementing
the [PollableSource Interface](https://flume.apache.org/releases/content/1.7.0/apidocs/org/apache/flume/PollableSource.html).

## Settings

The following settings are configurable:

- **dateStart**: The start date from which the source begins polling earthquake events. If not defined, it defaults to
  `01/01/2025`.

## Usage

To build the project, execute the following command

```bash
mvn clean package
```

