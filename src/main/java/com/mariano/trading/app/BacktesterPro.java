package com.mariano.trading.app;

import com.mariano.trading.model.Candle;

import java.util.List;

public class BacktesterPro {

    public static class Result {
        public int trades;
        public int wins;
        public int losses;

        public double winRatePct;
        public double totalReturnPct;   // equity compuesta
        public double maxDrawdownPct;

        public double avgTradePct;
        public double bestTradePct;
        public double worstTradePct;
    }

    /**
     * Backtest más realista (close-based):
     * - Entrada: Estado ENTRAR
     * - Salida: Estado SALIR o close <= stopFinal (ATR initial/trailing)
     * - 1 posición a la vez
     * - Equity compuesta y max drawdown aproximado
     */
    public Result run(
            List<Candle> candles,
            List<Double> sma20,
            List<Double> sma50,
            List<Double> rsi14,
            List<Double> atr14,
            double initialAtrMult,
            double trailingAtrMult
    ) {
        DecisionEngine engine = new DecisionEngine();
        RiskManager rm = new RiskManager();

        boolean inPos = false;
        double entry = 0.0;
        double highestClose = 0.0;
        double stopFinal = 0.0;

        int trades = 0, wins = 0, losses = 0;

        double equity = 1.0;
        double peak = 1.0;
        double maxDd = 0.0;

        double sumTrades = 0.0;
        double best = Double.NEGATIVE_INFINITY;
        double worst = Double.POSITIVE_INFINITY;

        for (int i = 0; i < candles.size(); i++) {
            Double s20 = sma20.get(i);
            Double s50 = sma50.get(i);
            Double rsi = rsi14.get(i);
            Double atr = atr14.get(i);
            if (s20 == null || s50 == null || rsi == null || atr == null) continue;

            double close = candles.get(i).getClose();

            DecisionEngine.Decision d = engine.decide(close, s20, s50, rsi, atr, inPos);

            if (!inPos) {
                if (d.estado == DecisionEngine.Estado.ENTRAR) {
                    inPos = true;
                    entry = close;
                    highestClose = close;

                    RiskManager.RiskPlan plan = rm.buildOpenPositionPlan(
                            entry, close, atr,
                            initialAtrMult,
                            highestClose,
                            trailingAtrMult,
                            1_000_000, 0.01
                    );
                    stopFinal = plan.stopLoss;
                }
            } else {
                if (close > highestClose) highestClose = close;

                RiskManager.RiskPlan plan = rm.buildOpenPositionPlan(
                        entry, close, atr,
                        initialAtrMult,
                        highestClose,
                        trailingAtrMult,
                        1_000_000, 0.01
                );
                stopFinal = plan.stopLoss;

                boolean stopHit = close <= stopFinal;
                boolean engineExit = d.estado == DecisionEngine.Estado.SALIR;

                if (stopHit || engineExit) {
                    double exit = close;
                    double tradePct = (exit - entry) / entry * 100.0;

                    trades++;
                    sumTrades += tradePct;
                    if (tradePct > best) best = tradePct;
                    if (tradePct < worst) worst = tradePct;

                    if (tradePct > 0) wins++;
                    else losses++;

                    equity *= (1.0 + tradePct / 100.0);

                    if (equity > peak) peak = equity;
                    double dd = (peak - equity) / peak;
                    if (dd > maxDd) maxDd = dd;

                    inPos = false;
                    entry = 0.0;
                    highestClose = 0.0;
                    stopFinal = 0.0;
                }
            }
        }

        Result r = new Result();
        r.trades = trades;
        r.wins = wins;
        r.losses = losses;
        r.winRatePct = (trades == 0) ? 0.0 : (wins * 100.0) / trades;
        r.totalReturnPct = (equity - 1.0) * 100.0;
        r.maxDrawdownPct = maxDd * 100.0;
        r.avgTradePct = (trades == 0) ? 0.0 : (sumTrades / trades);
        r.bestTradePct = (trades == 0) ? 0.0 : best;
        r.worstTradePct = (trades == 0) ? 0.0 : worst;
        return r;
    }
}