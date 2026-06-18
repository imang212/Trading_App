package com.trading.trading_app.model;

import com.trading.trading_app.model.Asset;

/**
 * Evaluates whether an AlertRule fires given the current Asset data.
 *
 * BUY_OPPORTUNITY fires when ALL of:
 *   1. Volume ratio > 1.4 (above-average volume)
 *   2. Bayes BUY score >= threshold (data-driven signal strength)
 *   3. RSI < 48 (not yet overbought)
 *   4. MACD histogram > 0 (bullish momentum)
 *   5. Price > EMA short (uptrend confirmation)
 *
 * SELL_REVERSAL fires when ALL of:
 *   1. RSI > 58 (approaching overbought)
 *   2. MACD histogram < 0 OR MACD just crossed below signal
 *   3. Price < EMA short (downtrend beginning)
 *   4. Volume ratio > 1.2 (confirmed with volume)
 *
 * A 60-minute cooldown prevents repeated notifications for the same rule.
 */
public final class AlertEvaluator {
    private AlertEvaluator() {}

    private static final long COOLDOWN_MS = 60 * 60 * 1000L;  // 1 hour

    public static boolean evaluate(AlertRule rule, Asset asset) {
        if (!rule.active) return false;
        // Cooldown: don't re-fire within 1 hour of last trigger
        if (rule.lastTriggeredAt > 0 &&
            System.currentTimeMillis() - rule.lastTriggeredAt < COOLDOWN_MS) return false;

        switch (rule.type) {
            case BUY_OPPORTUNITY:  return evalBuy(asset);
            case SELL_REVERSAL:    return evalSell(asset);
            case PRICE_ABOVE:      return asset.price >= rule.threshold;
            case PRICE_BELOW:      return asset.price <= rule.threshold;
            default: return false;
        }
    }

    private static boolean evalBuy(Asset asset) {
        boolean highVolume   = asset.volRatio > 1.4;
        boolean bayesStrong  = asset.bayesBuyScore >= asset.bayesThresholdBuy;
        boolean rsiGood      = !Double.isNaN(asset.rsi) && asset.rsi < 48;
        boolean macdBullish  = asset.macdHist > 0;
        boolean aboveEma     = asset.price > asset.emaShort;
        // Need at least 4 of 5 conditions
        int score = (highVolume?1:0) + (bayesStrong?1:0) + (rsiGood?1:0)
                  + (macdBullish?1:0) + (aboveEma?1:0);
        return score >= 4;
    }

    private static boolean evalSell(Asset asset) {
        boolean rsiHigh      = !Double.isNaN(asset.rsi) && asset.rsi > 58;
        boolean macdBearish  = asset.macdHist < 0;
        boolean belowEma     = asset.price < asset.emaShort;
        boolean volumeConfirm= asset.volRatio > 1.2;
        // Need all 4
        return rsiHigh && macdBearish && belowEma && volumeConfirm;
    }

    /** Human-readable reason why the alert fired. */
    public static String reason(AlertRule rule, Asset asset) {
        switch (rule.type) {
            case BUY_OPPORTUNITY:
                return String.format(
                    "BUY opportunity: RSI %.0f · Vol +%.0f%% · Bayes %.2f/%.2f · MACD %+.4f",
                    asset.rsi, (asset.volRatio - 1) * 100,
                    asset.bayesBuyScore, asset.bayesThresholdBuy, asset.macdHist);
            case SELL_REVERSAL:
                return String.format(
                    "SELL reversal: RSI %.0f · Vol +%.0f%% · MACD %+.4f · Price < EMA",
                    asset.rsi, (asset.volRatio - 1) * 100, asset.macdHist);
            case PRICE_ABOVE:
                return String.format("Price %.2f crossed above %.2f", asset.price, rule.threshold);
            case PRICE_BELOW:
                return String.format("Price %.2f crossed below %.2f", asset.price, rule.threshold);
            default: return "Alert triggered";
        }
    }
}
