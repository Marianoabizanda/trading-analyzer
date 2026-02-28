package com.mariano.trading.api;

import com.mariano.trading.app.*;
import com.mariano.trading.data.StooqClient;
import com.mariano.trading.indicators.ATR;
import com.mariano.trading.indicators.RSI;
import com.mariano.trading.indicators.SMA;
import com.mariano.trading.model.Candle;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalysisService {

    private final StooqClient client = new StooqClient();
    private final SMA sma = new SMA();
    private final RSI rsiCalc = new RSI();
    private final ATR atrCalc = new ATR();

    private final DecisionEngine engine = new DecisionEngine();
    private final RiskManager rm = new RiskManager();

    private final FundamentalsClientMock fundClient = new FundamentalsClientMock();
    private final FundamentalScorer fundScorer = new FundamentalScorer();

    // parámetros default (después los pasamos por query si querés)
    private final double capital = 1_000_000;
    private final double riskPct = 0.01;
    private final double atrMult = 2.0;

    public AnalyzeResponse analyze(String ticker) {
        ticker = ticker.toLowerCase().trim();

        List<Candle> candles = client.fetchDailyCandles(ticker);
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para ticker=" + ticker);
        }

        List<Double> sma20 = sma.calculate(candles, 20);
        List<Double> sma50 = sma.calculate(candles, 50);
        List<Double> rsi14 = rsiCalc.calculate(candles, 14);
        List<Double> atr14 = atrCalc.calculate(candles, 14);

        int last = candles.size() - 1;
        Candle lastCandle = candles.get(last);

        Double lastSma20 = sma20.get(last);
        Double lastSma50 = sma50.get(last);
        Double lastRsi14 = rsi14.get(last);
        Double lastAtr14 = atr14.get(last);

        if (lastSma20 == null || lastSma50 == null || lastRsi14 == null || lastAtr14 == null) {
            throw new IllegalArgumentException("No hay suficientes datos para calcular indicadores en el último día");
        }

        double close = lastCandle.getClose();

        // En API arrancamos sin posición (después lo conectamos a PositionStore si querés)
        boolean inPosition = false;

        DecisionEngine.Decision decision =
                engine.decide(close, lastSma20, lastSma50, lastRsi14, lastAtr14, inPosition);

        // fundamentals
        FundamentalData f = fundClient.get(ticker);
        int fundScore = fundScorer.score(f);


        int finalScore = (int) Math.round(decision.score * 0.65 + fundScore * 0.35);

        // riesgo (hipotético)
        RiskManager.RiskPlan plan = rm.buildPlan(close, lastAtr14, atrMult, capital, riskPct);

        AnalyzeResponse resp = new AnalyzeResponse();
        resp.ticker = ticker.toUpperCase();
        resp.date = lastCandle.getDate();
        resp.fundamentals = f;
        resp.fundamentalReport = fundScorer.report(f);

        resp.close = close;
        resp.sma20 = lastSma20;
        resp.sma50 = lastSma50;
        resp.rsi14 = lastRsi14;
        resp.atr14 = lastAtr14;

        resp.techScore = decision.score;
        resp.fundScore = fundScore;
        resp.finalScore = finalScore;

        DecisionEngine.Estado finalEstado = decision.estado;

        // Ajuste por fundamentals / finalScore (solo si NO estás en posición)
        if (!inPosition) {

            // Si el técnico dice ENTRAR pero fundamentals muy malos -> bajar a VIGILAR
            if (finalEstado == DecisionEngine.Estado.ENTRAR && fundScore < 40) {
                finalEstado = DecisionEngine.Estado.VIGILAR;
            }

            // Si finalScore muy bueno -> permitir ENTRAR aunque técnico sea VIGILAR
            if (finalScore >= 70 && finalEstado == DecisionEngine.Estado.VIGILAR) {
                finalEstado = DecisionEngine.Estado.ENTRAR;
            }

            // Si finalScore muy malo -> EVITAR (filtro duro)
            if (finalScore <= 30) {
                finalEstado = DecisionEngine.Estado.EVITAR;
            }

            // Caso: fundamentals fuertes pero técnico aún flojo -> al menos VIGILAR
            if (finalEstado == DecisionEngine.Estado.EVITAR && fundScore >= 70 && decision.score >= 35) {
                finalEstado = DecisionEngine.Estado.VIGILAR;
            }
        }

        resp.estado = finalEstado;

        resp.resumen = switch (finalEstado) {
            case ENTRAR -> "ENTRAR: técnico + fundamentos acompañan. Entrar con plan.";
            case VIGILAR -> "VIGILAR: fundamentos/técnico mixtos. Esperar confirmación antes de entrar.";
            case MANTENER -> "MANTENER: mantener con stop/trailing.";
            case SALIR -> "SALIR: señales de deterioro. Cerrar o reducir.";
            case EVITAR -> "EVITAR: setup flojo/arriesgado por ahora.";
        };
        resp.reporte = decision.reporte;

        resp.stopLoss = plan.stopLoss;
        resp.takeProfit = plan.takeProfit;
        resp.positionSizeShares = plan.positionSizeShares;

        return resp;
    }

    public java.util.List<ScanResultDto> scan(java.util.List<String> tickers) {

        java.util.List<ScanResultDto> out = new java.util.ArrayList<>();

        for (String t : tickers) {
            if (t == null) continue;
            String ticker = t.toLowerCase().trim();
            if (ticker.isEmpty()) continue;

            try {
                AnalyzeResponse a = analyze(ticker); // reutilizamos el analyze

                ScanResultDto r = new ScanResultDto();
                r.ticker = a.ticker;
                r.date = a.date;
                r.close = a.close;

                r.techScore = a.techScore;
                r.fundScore = a.fundScore;
                r.finalScore = a.finalScore;

                r.estado = a.estado;
                r.resumen = a.resumen;

                out.add(r);

            } catch (Exception ignored) {
                // si un ticker falla, lo salteamos para que no caiga todo el scan
            }
        }

        out.sort((a, b) -> {
            int pa = estadoPriority(a.estado);
            int pb = estadoPriority(b.estado);

            // primero prioridad de estado (DESC)
            if (pb != pa) return Integer.compare(pb, pa);

            // luego finalScore (DESC)
            return Integer.compare(b.finalScore, a.finalScore);
        });

        // top 10
        if (out.size() > 10) {
            return out.subList(0, 10);
        }
        return out;
    }

    private int estadoPriority(com.mariano.trading.app.DecisionEngine.Estado e) {
        if (e == null) return 0;
        return switch (e) {
            case ENTRAR -> 5;
            case SALIR -> 4;
            case VIGILAR -> 3;
            case MANTENER -> 2;
            case EVITAR -> 1;
        };
    }
}