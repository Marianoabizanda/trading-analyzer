package com.mariano.trading.app;

public class RiskManager {

    public static class RiskPlan {
        public double stopLoss;           // stop final sugerido (max entre inicial y trailing)
        public double initialStop;        // stop inicial por ATR
        public double trailingStop;       // stop trailing (por max desde entrada)
        public double takeProfit;         // objetivo simple
        public double riskPerShare;       // riesgo por acción usando stop final
        public double riskBudget;         // capital * riskPct
        public long positionSizeShares;   // tamaño sugerido
        public double rMultiple;          // (takeProfit - entry) / (entry - stopFinal)
    }

    // Para ENTRADAS (como ya venías)
    public RiskPlan buildPlan(double entryPrice, double atr, double atrMult, double capital, double riskPct) {
        RiskPlan p = new RiskPlan();

        p.riskBudget = capital * riskPct;
        p.initialStop = entryPrice - (atrMult * atr);

        // En entrada todavía no hay trailing: lo igualamos al inicial
        p.trailingStop = p.initialStop;
        p.stopLoss = p.initialStop;

        p.riskPerShare = Math.max(0.01, entryPrice - p.stopLoss);
        p.positionSizeShares = (long) Math.floor(p.riskBudget / p.riskPerShare);

        // Take profit simple (ej: 3R)
        double R = p.riskPerShare;
        p.takeProfit = entryPrice + 3.0 * R;

        p.rMultiple = (p.takeProfit - entryPrice) / (entryPrice - p.stopLoss);

        return p;
    }

    /**
     * Para POSICIÓN ABIERTA:
     * - highestCloseSinceEntry: máximo close desde que entraste
     * - trailingAtrMult: trailing = highestClose - trailingAtrMult*ATR
     */
    public RiskPlan buildOpenPositionPlan(
            double entryPrice,
            double currentPrice,
            double atr,
            double initialAtrMult,
            double highestCloseSinceEntry,
            double trailingAtrMult,
            double capital,
            double riskPct
    ) {
        RiskPlan p = new RiskPlan();

        p.riskBudget = capital * riskPct;

        // Stop inicial “de manual”
        p.initialStop = entryPrice - (initialAtrMult * atr);

        // Trailing stop por ATR desde el máximo desde entrada
        p.trailingStop = highestCloseSinceEntry - (trailingAtrMult * atr);

        // Stop final: el que más te protege (nunca bajar el stop)
        p.stopLoss = Math.max(p.initialStop, p.trailingStop);

        p.riskPerShare = Math.max(0.01, currentPrice - p.stopLoss);
        p.positionSizeShares = (long) Math.floor(p.riskBudget / p.riskPerShare);

        // Take profit simple: 3R desde la entrada, usando el stop FINAL como R
        double R = Math.max(0.01, entryPrice - p.stopLoss);
        p.takeProfit = entryPrice + 3.0 * R;

        p.rMultiple = (p.takeProfit - entryPrice) / (entryPrice - p.stopLoss);

        return p;
    }
}