package com.mariano.trading.indicators;

import com.mariano.trading.model.Candle;

import java.util.ArrayList;
import java.util.List;

public class RSI {

    public List<Double> calculate(List<Candle> candles, int period) {

        List<Double> rsiValues = new ArrayList<>(candles.size());

        double gainSum = 0;
        double lossSum = 0;

        // Los primeros valores no se pueden calcular
        rsiValues.add(null);

        for (int i = 1; i < candles.size(); i++) {

            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();

            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);

            if (i <= period) {
                gainSum += gain;
                lossSum += loss;
                rsiValues.add(null);
            } else {
                gainSum = (gainSum * (period - 1) + gain) / period;
                lossSum = (lossSum * (period - 1) + loss) / period;

                double rs = lossSum == 0 ? 0 : gainSum / lossSum;
                double rsi = lossSum == 0 ? 100 : 100 - (100 / (1 + rs));

                rsiValues.add(rsi);
            }
        }

        return rsiValues;
    }
}