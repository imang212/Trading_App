package com.trading.trading_app.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * One user-defined price/signal alert rule.
 *
 * Types:
 *   BUY_OPPORTUNITY  — fires when high volume + Bayes BUY score above threshold + RSI < 45
 *   SELL_REVERSAL    — fires when RSI > 60 + MACD crosses down + price < EMA
 *   PRICE_ABOVE      — simple price level alert (above threshold)
 *   PRICE_BELOW      — simple price level alert (below threshold)
 */
public class AlertRule implements Serializable {
    public enum Type { BUY_OPPORTUNITY, SELL_REVERSAL, PRICE_ABOVE, PRICE_BELOW }

    public String id;
    public String assetName;
    public String ticker;
    public String profile;
    public Type   type;
    public double threshold;        // price level (for PRICE_ABOVE / PRICE_BELOW)
    public boolean active = true;
    public long  createdAt;
    public long  lastTriggeredAt;   // 0 = never triggered
    public int   triggerCount;

    public AlertRule() {}

    public static AlertRule buyOpportunity(String assetName, String ticker, String profile) {
        AlertRule r = new AlertRule();
        r.id = UUID.randomUUID().toString();
        r.assetName = assetName; r.ticker = ticker; r.profile = profile;
        r.type = Type.BUY_OPPORTUNITY;
        r.threshold = 0;
        r.createdAt = System.currentTimeMillis();
        return r;
    }

    public static AlertRule sellReversal(String assetName, String ticker, String profile) {
        AlertRule r = new AlertRule();
        r.id = UUID.randomUUID().toString();
        r.assetName = assetName; r.ticker = ticker; r.profile = profile;
        r.type = Type.SELL_REVERSAL;
        r.threshold = 0;
        r.createdAt = System.currentTimeMillis();
        return r;
    }

    public static AlertRule priceAbove(String assetName, String ticker, double price) {
        AlertRule r = new AlertRule();
        r.id = UUID.randomUUID().toString();
        r.assetName = assetName; r.ticker = ticker;
        r.type = Type.PRICE_ABOVE; r.threshold = price;
        r.createdAt = System.currentTimeMillis();
        return r;
    }

    public static AlertRule priceBelow(String assetName, String ticker, double price) {
        AlertRule r = new AlertRule();
        r.id = UUID.randomUUID().toString();
        r.assetName = assetName; r.ticker = ticker;
        r.type = Type.PRICE_BELOW; r.threshold = price;
        r.createdAt = System.currentTimeMillis();
        return r;
    }

    public String typeLabel() {
        switch (type) {
            case BUY_OPPORTUNITY: return "🟢 BUY Opportunity";
            case SELL_REVERSAL:   return "🔴 SELL Reversal";
            case PRICE_ABOVE:     return "⬆ Price Above";
            case PRICE_BELOW:     return "⬇ Price Below";
            default: return type.name();
        }
    }
}
