package com.trading.trading_app.model;

import java.io.Serializable; import java.util.List;
/** Core data model for a tradable asset. Mirrors the signal dict produced by the Streamlit dashboard's load_signals().*/
public class Asset implements Serializable {
    // Identity
    public String name, ticker; // "GC=F", "NVDA", …
    public String profile; // "COMMODITY" | "TECH" | "DEFENSIVE" | "FOREX_IDX" | "CRYPTO"
    public String currencySymbol = "$";
    // Price
    public double price, priceChange; // % vs previous bar
    // Signal
    public String signal; // "BUY" | "SELL" | "NEU"
    public int buyScore, sellScore;
    public int bayesBuyScore, bayesSellScore; // raw, weighted 0-5
    public int bayesNeuScore;   // 0-100 neutral probability
    public double bayesBuyProb, bayesSellProb; // raw 0.0-1.0

    // Indicator conditions (true = bullish)
    public boolean condMA, condRSI, condBB, condMACD, condATR;
    // Indicator values
    public double rsi, rsiMid, bbPct, bbUpper, bbLower, bbMid, macd, macdSig, macdHist, emaShort, emaLong, smaShort, atr;
    public double buyLimit, stopLoss, takeProfit1, sellTarget, riskUsd; // Order levels
    // Speed & volume
    public double roc, atrChg, bodyRatio, volRatio, volNow, volAvg; // Rate of change last 10 bars, ATR change %, candle body vs 20-bar avg, volume vs 20-bar avg
    public String obvSignal;    // "BULLISH" | "BEARISH" | "DIVERGENCE ▲" | "DIVERGENCE ▼"
    // Profile params (from PROFILES dict)
    public int maShort, maLong, rsiOB, rsiOS;
    // OHLCV history (for charts)
    public List<OhlcBar> ohlcHistory;
    // Timestamp
    public long fetchedAt;      // epoch ms
    // Convenience helpers
    public boolean isBuy() { return "BUY".equals(signal); }
    public boolean isSell() { return "SELL".equals(signal); }
    public boolean isNeu() { return "NEU".equals(signal); }
    public int signalColor() { if (isBuy()) return 0xFF2ECC71; if (isSell()) return 0xFFE74C3C;  return 0xFFF39C12; }
    /** Pct distance from current price to a target level.*/
    public double pctTo(double level) {
        return price > 0 ? (level - price) / price * 100 : 0;
    }
    /** Risk-reward ratio to a given target from buy limit.*/
    public double rrTo(double target) { double riskPer = buyLimit - stopLoss; return riskPer > 0 ? Math.abs((target - buyLimit) / riskPer) : 0; }
    /** One OHLCV bar used for charting.*/
    public static class OhlcBar implements Serializable {
        public long timestamp; // epoch ms
        public float open, high, low, close;
        public long volume;
        public int signalFlag; // 1=BUY, -1=SELL, 0=none
        public float emaShort, emaLong, bbUpper, bbLower, rsi, macd, macdSig, macdHist, atr;
    }
}