package com.mariano.trading.model;

import java.time.LocalDate;

public class Candle {

    private LocalDate date;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;

    public Candle(LocalDate date, double open, double high, double low, double close, double volume) {
        this.date = date;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public LocalDate getDate() {
        return date;
    }

    public double getOpen() {
        return open;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }

    public double getClose() {
        return close;
    }

    public double getVolume() {
        return volume;
    }
}