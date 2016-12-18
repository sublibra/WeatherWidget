package se.wtm.sublibra.weatherwidget;
import java.util.Date;

import java.text.SimpleDateFormat;

/**
 * Data object containing data relevant for a weather sensor
 */
public class WeatherData {

    private double temperature;
    private int humidity;
    private long lastUpdated=0;

    public WeatherData() {

    }

    public WeatherData(int humidity, double temperature, long lastUpdated) {
        this.humidity = humidity;
        this.temperature = temperature;
        this.lastUpdated = lastUpdated;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getHumidity() {
        return humidity;
    }

    public void setHumidity(int humidity) {
        this.humidity = humidity;
    }

    /**
     * Get unixstamp date
     * @return timestamp in unix format
     */
    public long getLastUpdated() {
        return lastUpdated;
    }

    public String getLastUpdatedString(){
        // Epoch supplied by tellprox is in seconds. Java needs milliseconds
        Date updateDate = new Date(lastUpdated * 1000L);
        return new SimpleDateFormat("EEE, d MMM HH:mm").format(updateDate);
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String toString(){
        return Double.toString(temperature)
                + ":" + Integer.toString(humidity)
                + " timestamp:" + getLastUpdatedString();
    }
}
