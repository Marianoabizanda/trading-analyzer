package com.mariano.trading.app;

public class FundamentalScorer {

    public int score(FundamentalData f) {
        if (f == null) return 50; // neutral si no hay datos

        int score = 50;

        // PE (valoración)
        if (f.pe < 20) score += 15;
        else if (f.pe < 30) score += 5;
        else if (f.pe > 50) score -= 15;
        else if (f.pe > 35) score -= 5;

        // ROE (rentabilidad)
        if (f.roe > 0.30) score += 15;
        else if (f.roe > 0.20) score += 5;
        else if (f.roe < 0.10) score -= 10;

        // Deuda
        if (f.debtToEquity < 0.5) score += 10;
        else if (f.debtToEquity > 1.0) score -= 10;

        // Crecimiento
        if (f.revenueGrowth > 0.15) score += 10;
        else if (f.revenueGrowth < 0.03) score -= 5;

        if (score < 0) score = 0;
        if (score > 100) score = 100;

        return score;
    }

    public String report(FundamentalData f) {
        if (f == null) return "=== ANÁLISIS FUNDAMENTAL ===\nSin datos fundamentales para este ticker.\n";

        StringBuilder sb = new StringBuilder();
        sb.append("=== ANÁLISIS FUNDAMENTAL (MOCK) ===\n");

        sb.append(String.format("- P/E: %.2f (%s)\n", f.pe,
                f.pe < 20 ? "barato" : f.pe < 30 ? "razonable" : f.pe > 50 ? "muy caro" : f.pe > 35 ? "caro" : "neutral"));

        sb.append(String.format("- ROE: %.2f (%s)\n", f.roe,
                f.roe > 0.30 ? "excelente" : f.roe > 0.20 ? "bueno" : f.roe < 0.10 ? "bajo" : "normal"));

        sb.append(String.format("- Debt/Equity: %.2f (%s)\n", f.debtToEquity,
                f.debtToEquity < 0.5 ? "baja deuda" : f.debtToEquity > 1.0 ? "deuda alta" : "moderada"));

        sb.append(String.format("- Revenue Growth: %.2f (%s)\n", f.revenueGrowth,
                f.revenueGrowth > 0.15 ? "alto" : f.revenueGrowth < 0.03 ? "bajo" : "moderado"));

        sb.append("\nInterpretación:\n");
        sb.append("- Este score es un filtro “sanidad/fortaleza”. No es timing (eso lo da el técnico).\n");

        return sb.toString();
    }
}