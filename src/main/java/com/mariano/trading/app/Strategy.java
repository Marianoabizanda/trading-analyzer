package com.mariano.trading.app;

import java.util.List;

public class Strategy {

    /**
     * Señal basada en:
     * - BUY cuando SMA20 cruza por encima de SMA50 (golden cross)
     * - SELL cuando SMA20 cruza por debajo de SMA50 (death cross)
     * - Filtro RSI:
     *      BUY solo si RSI < 60 (no comprar sobrecalentado)
     *      SELL solo si RSI > 40 (no vender hiper castigado)
     */
    public Signal signalAt(int i, List<Double> sma20, List<Double> sma50, List<Double> rsi14) {

        if (i <= 0) return Signal.HOLD;

        Double aPrev = sma20.get(i - 1);
        Double bPrev = sma50.get(i - 1);
        Double aNow  = sma20.get(i);
        Double bNow  = sma50.get(i);
        Double rsi   = rsi14.get(i);

        // si todavía no hay datos suficientes
        if (aPrev == null || bPrev == null || aNow == null || bNow == null || rsi == null) {
            return Signal.HOLD;
        }

        boolean crossUp = aPrev <= bPrev && aNow > bNow;     // cruza hacia arriba
        boolean crossDown = aPrev >= bPrev && aNow < bNow;   // cruza hacia abajo

        if (crossUp && rsi < 60) return Signal.BUY;
        if (crossDown && rsi > 40) return Signal.SELL;

        return Signal.HOLD;
    }   
}