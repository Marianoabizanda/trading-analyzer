package com.mariano.trading.api;

import com.mariano.trading.app.DecisionEngine;

import java.time.LocalDate;

public class ScanResultDto {
    public String ticker;
    public LocalDate date;

    public double close;

    public int techScore;
    public int fundScore;
    public int finalScore;

    public DecisionEngine.Estado estado;
    public String resumen;
}