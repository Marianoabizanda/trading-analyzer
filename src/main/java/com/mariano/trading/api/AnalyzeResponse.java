package com.mariano.trading.api;

import com.mariano.trading.app.DecisionEngine;
import com.mariano.trading.app.FundamentalData;

import java.time.LocalDate;

public class AnalyzeResponse {
    public String ticker;
    public LocalDate date;

    public double close;
    public double sma20;
    public double sma50;
    public double rsi14;
    public double atr14;

    public int techScore;
    public int fundScore;
    public int finalScore;
    public FundamentalData fundamentals;
    public String fundamentalReport;

    public DecisionEngine.Estado estado;
    public String resumen;
    public String reporte; // el reporte “trader” completo

    // riesgo (si quisieras entrar hoy)
    public double stopLoss;
    public double takeProfit;
    public long positionSizeShares;
}