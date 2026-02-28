package com.mariano.trading.indicators;

import com.mariano.trading.model.Candle;

import java.util.ArrayList;
import java.util.List;

public class SMA {

    /**
     * Devuelve una lista de SMA del mismo tamaño que candles.
     * En los primeros días donde no alcanza el período, devuelve null.
     */
    public List<Double> calculate(List<Candle> candles, int period) {
        List<Double> sma = new ArrayList<>(candles.size());
        double sum = 0;

        for (int i = 0; i < candles.size(); i++) {
            sum += candles.get(i).getClose();

            // Todavía no hay suficientes datos para calcular el promedio
            if (i < period - 1) {
                sma.add(null);
                continue;
            }

            // Cuando ya pasaron "period" elementos, restamos el que quedó afuera de la ventana
            if (i >= period) {
                sum -= candles.get(i - period).getClose();
            }

            sma.add(sum / period);
        }
        return sma;
    }
}