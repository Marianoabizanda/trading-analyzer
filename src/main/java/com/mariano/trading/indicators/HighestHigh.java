package com.mariano.trading.indicators;

import com.mariano.trading.model.Candle;

import java.util.ArrayList;
import java.util.List;

public class HighestHigh {

    /**
     * Devuelve una lista del mismo tamaño que candles.
     * Para cada índice i (>= period-1), devuelve el máximo "high"
     * de los últimos period candles (incluyendo el i).
     * Si no hay suficientes datos, devuelve null.
     */
    public List<Double> calculate(List<Candle> candles, int period) {
        List<Double> out = new ArrayList<>(candles.size());

        for (int i = 0; i < candles.size(); i++) {
            if (i < period - 1) {
                out.add(null);
                continue;
            }

            double max = Double.NEGATIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                max = Math.max(max, candles.get(j).getHigh());
            }
            out.add(max);
        }

        return out;
    }
}