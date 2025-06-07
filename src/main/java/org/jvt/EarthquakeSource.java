package org.jvt;

import org.apache.flume.Context;
import org.apache.flume.conf.Configurable;
import org.apache.flume.source.AbstractSource;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Scanner;

public class EarthquakeSource extends AbstractSource implements Configurable {
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
    private final String baseUrl = "https://earthquake.usgs.gov/fdsnws/event/1";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(EarthquakeSource.class);
    private Date dateStart = new GregorianCalendar(2025, Calendar.JANUARY, 1).getTime();
    private Date lastTimeCheckedAt = new Date();
    private int count = 0;
    private int maxAllowed = 0;

    @Override
    public void configure(Context context) {
        String dateStart = context.getString("dateStart", this.dateToString(this.dateStart));
        this.dateStart = this.stringToDate(dateStart);
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
        endDate = (endDate != null) ? endDate : this.lastTimeCheckedAt;
        LOGGER.info("Setting event counter for end date {}", endDate);

        String urlStr = String.format("%s/count?format=geojson&starttime=%s&endtime=%s",
                this.baseUrl,
                this.dateToString(this.dateStart), this.dateToString(endDate));
        JSONObject parse = this.makeRequest(urlStr);

        this.count = parse.getInt("count");
        this.maxAllowed = parse.getInt("maxAllowed");
        LOGGER.info("New event counter is {}", this.count);
    }

    private JSONObject getEvents() {
        LOGGER.info("Getting events...");
        Date endDate = this.getRebalancedEndDate(this.dateStart, this.lastTimeCheckedAt);
        String urlStr = String.format("%s/query?format=geojson&starttime=%s&endtime=%s", this.baseUrl, this.dateToString(this.dateStart), this.dateToString(endDate));
        this.dateStart = endDate;

        JSONObject result = this.makeRequest(urlStr);
        JSONObject metadata = result.getJSONObject("metadata");

        LOGGER.info("Number total of events {}", metadata.get("count"));
        LOGGER.debug("Event URL {}", metadata.get("url"));


        return result;
    }

    private Date getRebalancedEndDate(Date startDate, Date endDate) {
        Date newEndDate = endDate;

        while (this.count > this.maxAllowed) {
            long diffMilliseconds = newEndDate.getTime() - startDate.getTime();
            diffMilliseconds = diffMilliseconds / 2;
            long newEndDateTime = diffMilliseconds + startDate.getTime();

            newEndDate = new Date(newEndDateTime);

            this.setCounters(newEndDate);
        }

        System.out.println(this.count);

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
}
