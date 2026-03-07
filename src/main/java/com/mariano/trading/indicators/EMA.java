package com.mariano.trading.indicators;

import com.mariano.trading.model.Candle;

import java.util.ArrayList;
import java.util.List;

public class EMA {

    public List<Double> calculate(List<Candle> candles, int period) {
        List<Double> out = new ArrayList<>();
        if (candles == null || candles.isEmpty() || period <= 0) return out;

        double k = 2.0 / (period + 1.0);

        Double ema = null;
        double sum = 0;

        for (int i = 0; i < candles.size(); i++) {
            double close = candles.get(i).getClose();

            // antes de tener "period" datos, no hay EMA confiable
            if (i < period) {
                sum += close;
                out.add(null);

                // cuando llegamos al punto period-1, inicializamos EMA con SMA(period)
                if (i == period - 1) {
                    ema = sum / period;
                    out.set(i, ema);
                }
                continue;
            }

            // EMA recursiva
            ema = (close - ema) * k + ema;
            out.add(ema);
        }

        return out;
    }
}