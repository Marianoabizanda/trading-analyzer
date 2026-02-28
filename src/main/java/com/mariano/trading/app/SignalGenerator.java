package com.mariano.trading.app;

import java.util.ArrayList;
import java.util.List;

public class SignalGenerator {

    /**
     * Convierte estados del DecisionEngine en señales BUY/SELL/HOLD para backtest.
     * Reglas:
     * - Si no estás en posición y estado=ENTRAR => BUY
     * - Si estás en posición y estado=SALIR => SELL
     * - Lo demás => HOLD
     *
     * IMPORTANTE: esta lógica emula "solo 1 posición a la vez".
     */
    public List<Signal> fromDecisions(
            List<Double> closes,
            List<Double> sma20,
            List<Double> sma50,
            List<Double> rsi14,
            List<Double> atr14
    ) {
        DecisionEngine engine = new DecisionEngine();
        List<Signal> signals = new ArrayList<>();

        boolean inPos = false;

        for (int i = 0; i < closes.size(); i++) {
            Double s20 = sma20.get(i);
            Double s50 = sma50.get(i);
            Double rsi = rsi14.get(i);
            Double atr = atr14.get(i);

            // Si no hay datos aún, HOLD
            if (s20 == null || s50 == null || rsi == null || atr == null) {
                signals.add(Signal.HOLD);
                continue;
            }

            double close = closes.get(i);

            DecisionEngine.Decision d = engine.decide(close, s20, s50, rsi, atr, inPos);

            if (!inPos && d.estado == DecisionEngine.Estado.ENTRAR) {
                signals.add(Signal.BUY);
                inPos = true;
            } else if (inPos && d.estado == DecisionEngine.Estado.SALIR) {
                signals.add(Signal.SELL);
                inPos = false;
            } else {
                signals.add(Signal.HOLD);
            }
        }

        return signals;
    }
}