package org.jvt;

import org.apache.flume.*;
import org.apache.flume.conf.Configurable;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.instrumentation.SourceCounter;
import org.apache.flume.source.AbstractSource;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EarthquakeSource extends AbstractSource implements Configurable, PollableSource {
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
    private final String baseUrl = "https://earthquake.usgs.gov/fdsnws/event/1";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(EarthquakeSource.class);
    private Date dateStart = new GregorianCalendar(2025, Calendar.JANUARY, 1).getTime();
    private int count = 0;
    private int maxAllowed = 0;
    private final long backOffIntervalMs = 60000;
    private SourceCounter sourceCounter;

    @Override
    public void configure(Context context) {
        String dateStart = context.getString("dateStart", this.dateToString(this.dateStart));


        this.dateStart = this.stringToDate(dateStart);

        if (sourceCounter == null) {
            sourceCounter = new SourceCounter(getName());
        }
    }

    @Override
    public synchronized void start() {
        LOGGER.info("Starting earthquake source...");

        this.setCounters(null);

        LOGGER.info("Earthquake source started...");

        super.start();
    }

    private String dateToString(Date date) {
        return date.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);

    }

    private Date stringToDate(String dateStr) {
        return java.sql.Timestamp.valueOf(LocalDateTime.parse(dateStr, this.formatter));
    }

    private void setCounters(Date endDate) {
        endDate = (endDate != null) ? endDate : new Date();
        LOGGER.info("Setting event counter for end date {}", endDate);

        String urlStr = String.format("%s/count?format=geojson&starttime=%s&endtime=%s",
                this.baseUrl,
                this.dateToString(this.dateStart), this.dateToString(endDate));
        JSONObject parse = this.makeRequest(urlStr);

        this.count = parse.getInt("count");
        this.maxAllowed = parse.getInt("maxAllowed");
        LOGGER.info("New event counter is {}", this.count);
    }

    private JSONArray getEvents() {
        LOGGER.info("Getting events...");
        Date endDate = this.getRebalancedEndDate(this.dateStart, new Date());
        String urlStr = String.format("%s/query?format=geojson&starttime=%s&endtime=%s", this.baseUrl, this.dateToString(this.dateStart), this.dateToString(endDate));

        JSONObject result = this.makeRequest(urlStr);
        JSONObject metadata = result.getJSONObject("metadata");
        JSONArray features = result.getJSONArray("features");

        LOGGER.info("Number total of events {}", metadata.get("count"));
        LOGGER.debug("Event URL {}", metadata.get("url"));

        this.dateStart = endDate;

        return features;
    }

    private Date getRebalancedEndDate(Date startDate, Date endDate) {
        Date newEndDate = endDate;
        int maxSupportedEvent = 300; // 2000 events can use a lot of heap memory

        while (count > maxAllowed || count > maxSupportedEvent) {
            long diffMillisecondsMs = (newEndDate.getTime() - startDate.getTime()) / 2;
            long newEndDateTime = diffMillisecondsMs + startDate.getTime();
            long minWindowDiffMs = 500;

            newEndDate = new Date(newEndDateTime);

            this.setCounters(newEndDate);

            if (diffMillisecondsMs < minWindowDiffMs) {
                LOGGER.error("Unable to reduce window further. Using minimal window.");
                break;
            }
        }

        return newEndDate;
    }

    private JSONObject makeRequest(String urlString) {
        try {
            URL url = new URI(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.connect();

            StringBuilder informationString = this.getHttpResponse(conn);

            return new JSONObject(informationString.toString());
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private StringBuilder getHttpResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        InputStream stream = conn.getInputStream();

        if (responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
            throw new RuntimeException("Error when making API call: " + responseCode);
        }

        StringBuilder bodyString = new StringBuilder();
        try (Scanner scanner = new Scanner(stream)) {
            while (scanner.hasNext()) {
                bodyString.append(scanner.nextLine());
            }
        }
        return bodyString;
    }

    @Override
    public Status process() {
        try {
            setCounters(null);
            JSONArray jsonArray = getEvents();
            int length = jsonArray.length();

            if (length == 0) {
                return Status.BACKOFF;
            }

            for (int i = 0; i < length; i++) {
                JSONObject quake = jsonArray.getJSONObject(i);
                byte[] body = quake.toString().getBytes(StandardCharsets.UTF_8);
                Event event = EventBuilder.withBody(body);
                getChannelProcessor().processEvent(event);
            }

            return Status.READY;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long getBackOffSleepIncrement() {
        return backOffIntervalMs;
    }

    @Override
    public long getMaxBackOffSleepInterval() {
        return backOffIntervalMs * 10;
    }
}
