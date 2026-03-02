export async function scanTickers(tickersCsv) {
  const r = await fetch(`/api/scan?tickers=${encodeURIComponent(tickersCsv)}`);
  if (!r.ok) throw new Error("Error en /api/scan");
  return r.json();
}

export async function analyzeTicker(ticker) {
  const r = await fetch(`/api/analyze?ticker=${encodeURIComponent(ticker)}`);
  if (!r.ok) throw new Error("Error en /api/analyze");
  return r.json();
}

export async function historyTicker(ticker, days = 60) {
  const r = await fetch(`/api/history?ticker=${encodeURIComponent(ticker)}&days=${days}`);
  if (!r.ok) throw new Error("Error en /api/history");
  return r.json();
}