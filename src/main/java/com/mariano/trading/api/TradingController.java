package com.mariano.trading.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TradingController {

    private final AnalysisService service;

    public TradingController(AnalysisService service) {
        this.service = service;
    }

    @GetMapping("/api/analyze")
    public AnalyzeResponse analyze(@RequestParam String ticker) {
        return service.analyze(ticker);
    }

    @GetMapping("/api/scan")
    public java.util.List<ScanResultDto> scan(@RequestParam String tickers) {

        java.util.List<String> list = java.util.Arrays.stream(tickers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        return service.scan(list);
    }
}