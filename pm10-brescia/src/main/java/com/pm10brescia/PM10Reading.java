package com.pm10brescia;

import java.time.LocalDateTime;

/**
 * Rappresenta una singola misurazione PM10:
 * - quando è stata rilevata (timestamp)
 * - da quale sensore
 * - il valore in µg/m³
 */
public class PM10Reading {

    private final LocalDateTime timestamp;
    private final String sensorName;
    private final double value;

    public PM10Reading(LocalDateTime timestamp, String sensorName, double value) {
        this.timestamp = timestamp;
        this.sensorName = sensorName;
        this.value = value;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSensorName()       { return sensorName; }
    public double getValue()            { return value; }
}
