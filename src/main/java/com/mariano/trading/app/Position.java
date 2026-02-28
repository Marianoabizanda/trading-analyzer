package com.mariano.trading.app;
import java.time.LocalDate;

public class Position {
    public String ticker;
    public double entryPrice;
    public LocalDate entryDate;      // yyyy-mm-dd (como te llega en Candle)
    public double highestClose;   // máximo close desde entrada
}