package com.mariano.trading.app;

import com.mariano.trading.model.Candle;

import java.util.List;

public class Backtester {

    public static class Result {
        public int trades;
        public int wins;
        public int losses;
        public double winRate;
        public double totalReturnPct;
    }

    /**
     * Backtest simple:
     * - Cuando aparece BUY: entra en close de ese día
     * - Cuando aparece SELL: sale en close de ese día
     * - Solo 1 posición a la vez (long)
     * - Calcula winrate y retorno acumulado (suma de % de cada trade)
     */
    public Result run(List<Candle> candles, List<Signal> signals) {
        Result r = new Result();

        boolean inPosition = false;
        double entryPrice = 0;

        for (int i = 0; i < candles.size(); i++) {
            Signal s = signals.get(i);
            double price = candles.get(i).getClose();

            if (!inPosition && s == Signal.BUY) {
                inPosition = true;
                entryPrice = price;
            } else if (inPosition && s == Signal.SELL) {
                inPosition = false;
                r.trades++;

                double tradeReturnPct = (price - entryPrice) / entryPrice * 100.0;
                r.totalReturnPct += tradeReturnPct;

                if (tradeReturnPct > 0) r.wins++;
                else r.losses++;
            }
        }

        if (r.trades > 0) r.winRate = (r.wins * 100.0) / r.trades;
        return r;
    }
}