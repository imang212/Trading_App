package com.trading.trading_app.util;
/**ProphetLite — lightweight time-series forecast without any external library.
 * Prophet (the Python library) is not available on Android. This class reimplements its core ideas:
 *   forecast = trend(t) + weekly_seasonality(t)
 * Trend:
 *   Linear regression on the last TREND_WINDOW bars (robust to outliers
 *   via Theil-Sen median slope estimator — same philosophy as Prophet).
 * Seasonality:
 *   Weekly: average residual per day-of-week (7 buckets).
 *   If bars represent intraday (4h/1h/…), the "day" bucket maps to
 *   bar-index mod 7 — still captures weekly cycles in intraday data.
 * Output:
 *   HORIZON = 30 forecast values, uncertainty band ± 1 MAE of residuals.
 */
public class ProphetLite {
    private ProphetLite() {}
    public static final int HORIZON = PredictionEngine.HORIZON, TREND_WINDOW = 90; // bars used for trend fit
    public static class ProphetResult {
        public double[] forecast = new double[HORIZON], upper = new double[HORIZON], lower = new double[HORIZON], trend = new double[HORIZON], seasonal = new double[HORIZON];
        public boolean valid = false;
        public static final String COLOR = "#FF6F00", LABEL = "Prophet (trend + seasonal)", SHORT = "Prophet";
    }
    public static ProphetResult forecast(double[] prices, long[] timestamps) {
        ProphetResult r = new ProphetResult();
        int n = prices.length; if (n < 14) return r; // need at least 2 weeks of data
        int window = Math.min(TREND_WINDOW, n);
        double[] px = prices; //use all prices for seasonability
        // 1. Trend: Theil-Sen slope on last TREND_WINDOW bars
        // Theil-Sen: median of all pairwise slopes — robust to outliers
        int wStart = n - window, pairCount = window * (window - 1) / 2;
        double[] slopes; // For performance limit pairs to at most 2000 (random sample if needed)
        if (pairCount <= 2000) {
            slopes = new double[pairCount]; int k = 0;
            for (int i = wStart; i < n - 1; i++)
                for (int j = i + 1; j < n; j++) slopes[k++] = (prices[j] - prices[i]) / (j - i);
        }
        else {
            slopes = new double[2000]; // Sample 2000 random pairs
            java.util.Random rng = new java.util.Random(7);
            for (int k = 0; k < 2000; k++) {
                int i = wStart + rng.nextInt(window - 1);
                int j = i + 1 + rng.nextInt(n - i - 1);
                slopes[k] = (prices[j] - prices[i]) / (j - i);
            }
        }
        java.util.Arrays.sort(slopes);
        double beta = slopes[slopes.length / 2]; // median slope
        // Intercept: median of (price[i] - beta*i) for i in window
        double[] intercepts = new double[window];
        for (int i = 0; i < window; i++) intercepts[i] = prices[wStart + i] - beta * (wStart + i);
        java.util.Arrays.sort(intercepts);
        double alpha = intercepts[intercepts.length / 2];
        // 2. Seasonal: weekly residual per day-of-week bucket
        double[] bucketSum = new double[7]; int[] bucketCnt = new int[7];
        for (int i = 0; i < n; i++) { // day_bucket = bar_index mod 7 (0..6)
            double trendVal = alpha + beta * i;
            double resid = prices[i] - trendVal;
            int bucket = i % 7;
            bucketSum[bucket] += resid;
            bucketCnt[bucket]++;
        }
        double[] seasonal = new double[7];
        for (int b = 0; b < 7; b++) seasonal[b] = bucketCnt[b] > 0 ? bucketSum[b] / bucketCnt[b] : 0;
        // 3. Uncertainty band: MAE of in-sample residuals
        double mae = 0;
        for (int i = wStart; i < n; i++) {
            double fitted = alpha + beta * i + seasonal[i % 7];
            mae += Math.abs(prices[i] - fitted);
        }
        mae /= window;
        // 4. Forecast for next HORIZON bars
        for (int h = 0; h < HORIZON; h++) {
            int futureIdx = n + h;
            double trendVal = alpha + beta * futureIdx, seasonalVal = seasonal[futureIdx % 7];
            double fc = trendVal + seasonalVal;
            r.trend[h] = trendVal; r.seasonal[h] = seasonalVal; r.forecast[h] = fc;
            r.upper[h] = fc + 1.5 * mae; r.lower[h] = fc - 1.5 * mae;
        }
        r.valid = true;
        return r;
    }
}
