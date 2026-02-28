package com.mariano.trading.indicators;

import com.mariano.trading.model.Candle;

import java.util.ArrayList;
import java.util.List;

public class ATR {

    public List<Double> calculate(List<Candle> candles, int period) {
        List<Double> atr = new ArrayList<>(candles.size());
        if (candles.isEmpty()) return atr;

        // ATR necesita TR (True Range)
        List<Double> trList = new ArrayList<>(candles.size());
        trList.add(null); // el primer día no tiene "prev close"

        for (int i = 1; i < candles.size(); i++) {
            double high = candles.get(i).getHigh();
            double low = candles.get(i).getLow();
            double prevClose = candles.get(i - 1).getClose();

            double tr = Math.max(high - low,
                    Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));

            trList.add(tr);
        }

        // Calculamos ATR con smoothing estilo Wilder
        for (int i = 0; i < candles.size(); i++) atr.add(null);

        // primer ATR: promedio simple de TR de los primeros "period" (desde i=1)
        if (candles.size() <= period) return atr;

        double sum = 0;
        for (int i = 1; i <= period; i++) {
            sum += trList.get(i);
        }
        double firstAtr = sum / period;
        atr.set(period, firstAtr);

        double prevAtr = firstAtr;
        for (int i = period + 1; i < candles.size(); i++) {
            double tr = trList.get(i);
            double currentAtr = (prevAtr * (period - 1) + tr) / period;
            atr.set(i, currentAtr);
            prevAtr = currentAtr;
        }

        return atr;
    }
}