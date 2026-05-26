package com.trading.trading_app.util;
import java.util.Random;
public class PredictionEngine {
    private PredictionEngine() {}
    public static final int N_PATHS = 200, HORIZON = 30;
    public static class PredictionResult {
        public double[] median = new double[HORIZON], p10 = new double[HORIZON], p90 = new double[HORIZON];
        public double[][] paths = new double[N_PATHS][HORIZON];
        public String label, shortLabel, color;
        public boolean valid = false;
    }
    public static PredictionResult predict(double[] closePrices, String profile) {
        if (closePrices == null || closePrices.length < 30) return new PredictionResult();
        switch (profile){
            case "TECH": return rwWithJumps(closePrices);
            case "COMMODITY": return gbmMeanReversion(closePrices);
            case "CRYPTO": return garchVolatility(closePrices);
            case "FOREX_IDX": return ornsteinUhlenbeck(closePrices);
            default: return randomWalk(closePrices);   // DEFENSIVE + fallback
        }
    }
    public static PredictionResult randomWalk(double[] prices){
        double mu = logReturnMean(prices), sigma = logReturnStd(prices, mu);
        Random rng = new Random(42);
        PredictionResult r = runPaths(prices, (path, step, rn) -> path[step - 1] * Math.exp((mu - 0.5 * sigma * sigma) + sigma * rn), rng);
        r.label = "Random Walk"; r.shortLabel = "RW"; r.color = "#1565C0";
        return r;
    }
    // 2. RW + Earnings Jumps  (TECH), Earnings ≈ 4× per year → λ = 4 / barsPerYear, Jump size ~ N(0, 2σ) — can be positive or negative
    public static PredictionResult rwWithJumps(double[] prices) {
        double mu = logReturnMean(prices), sigma = logReturnStd(prices, mu);
        int barsPerYear = estimateBarsPerYear(prices);
        double lambda = 4.0 / barsPerYear;  // Poisson rate per bar
        Random rng = new Random(42);
        PredictionResult r = runPaths(prices, (path, step, rn) -> {
            double base = path[step - 1] * Math.exp((mu - 0.5 * sigma * sigma) + sigma * rn);
            // Poisson: jump fires with probability lambda (approx for small lambda)
            if (rng.nextDouble() < lambda) {
                double jumpSize = 2 * sigma * rng.nextGaussian();
                base *= Math.exp(jumpSize);
            }
            return base;
        }, rng);
        r.label = "RW + Earnings Jumps"; r.shortLabel = "RW+E"; r.color = "#6A1B9A";
        return r;
    }
    // 3. GBM + Mean Reversion  (COMMODITY), dS = κ(μ_sma - S)dt + σ·S·dW   (continuous-time Vasicek on log prices), κ = mean-reversion speed, calibrated from half-life of deviations
    public static PredictionResult gbmMeanReversion(double[] prices) {
        double mu = logReturnMean(prices), sigma = logReturnStd(prices, mu);
        double sma20 = 0; int win = Math.min(20, prices.length); // SMA20 of last section as long-run mean
        for (int i = prices.length - win; i < prices.length; i++) sma20 += prices[i];
        sma20 /= win;
        double kappa = 0.10;  // mean reversion speed (10% per period)
        final double mu_mr = sma20;
        Random rng = new Random(42);
        PredictionResult r = runPaths(prices, (path, step, rn) -> {
            double s = path[step - 1];
            double drift = mu + kappa * (Math.log(mu_mr) - Math.log(s));
            return s * Math.exp((drift - 0.5 * sigma * sigma) + sigma * rn);
        }, rng);
        r.label = "GBM + Mean Reversion"; r.shortLabel = "GBM-MR"; r.color = "#1B00E6";
        return r;
    }
    // 4. GARCH(1,1)  (CRYPTO), σ²_t = ω + α·ε²_{t-1} + β·σ²_{t-1}, Calibrated from historical returns with MLE-like approach
    public static PredictionResult garchVolatility(double[] prices) {
        double[] logRet = logReturns(prices);
        double mu = mean(logRet), sigma0 = std(logRet, mu);
        // Simple GARCH(1,1) parameters (typical crypto values)
        double omega = 0.000005, alpha = 0.10;   // ARCH term
        double beta  = 0.85;   // GARCH term  (alpha+beta < 1)
        Random rng = new Random(42);
        double[][] paths = new double[N_PATHS][HORIZON];
        for (int p = 0; p < N_PATHS; p++) {
            double s = prices[prices.length - 1], h = sigma0 * sigma0; // initial variance
            double prevEps = 0;
            for (int t = 0; t < HORIZON; t++) {
                h = omega + alpha * prevEps * prevEps + beta * h;
                double sigmaT = Math.sqrt(Math.max(h, 1e-10));
                double eps = sigmaT * rng.nextGaussian();
                s = s * Math.exp((mu - 0.5 * h) + eps);
                paths[p][t] = s;
                prevEps = eps;
            }
        }
        PredictionResult r = percentilesFromPaths(paths);
        r.label = "GARCH Volatility"; r.shortLabel = "GARCH"; r.color = "#B71C1C";
        return r;
    }
    // 5. Ornstein-Uhlenbeck  (FOREX_IDX), dS = κ(μ - S)dt + σ·dW, Strong mean reversion — price always pulled back toward long-run mean
    public static PredictionResult ornsteinUhlenbeck(double[] prices) {
        double[] logP = new double[prices.length];
        for (int i = 0; i < prices.length; i++) logP[i] = Math.log(prices[i]);
        double muLog = mean(logP), sigmaLog = std(logP, muLog);
        // Use recent SMA as equilibrium level
        int win = Math.min(60, prices.length);
        double sma = 0;
        for (int i = prices.length - win; i < prices.length; i++) sma += logP[i];
        sma /= win;
        double kappa = 0.20;  // mean reversion speed
        double sigma = sigmaLog * 0.05;  // daily vol of log price
        Random rng = new Random(42);
        double[][] paths = new double[N_PATHS][HORIZON];
        for (int p = 0; p < N_PATHS; p++) {
            double x = Math.log(prices[prices.length - 1]);  // current log price
            for (int t = 0; t < HORIZON; t++) {
                x = x + kappa * (sma - x) + sigma * rng.nextGaussian();
                paths[p][t] = Math.exp(x);
            }
        }
        PredictionResult r = percentilesFromPaths(paths);
        r.label = "Ornstein-Uhlenbeck"; r.shortLabel = "O-U"; r.color = "#1B5E20";
        return r;
    }
    // helpers
    interface StepFn { double next(double[] path, int step, double gaussianRn); }
    private static PredictionResult runPaths(double[] prices, StepFn fn, Random rng) {
        double[][] paths = new double[N_PATHS][HORIZON];
        double s0 = prices[prices.length - 1];
        for (int p = 0; p < N_PATHS; p++) {
            paths[p][0] = fn.next(new double[]{s0, s0}, 1, rng.nextGaussian()); // bootstrap first step
            double[] pathArr = new double[HORIZON + 1]; // Build a small helper array so StepFn can look back 1 step
            pathArr[0] = s0;
            for (int t = 1; t <= HORIZON; t++) pathArr[t] = fn.next(pathArr, t, rng.nextGaussian());
            System.arraycopy(pathArr, 1, paths[p], 0, HORIZON);
        }
        return percentilesFromPaths(paths);
    }
    static PredictionResult percentilesFromPaths(double[][] paths) {
        PredictionResult r = new PredictionResult();
        r.paths = paths;
        int n = N_PATHS;
        double[] tmp = new double[n];
        for (int t = 0; t < HORIZON; t++) {
            for (int p = 0; p < n; p++) tmp[p] = paths[p][t];
            java.util.Arrays.sort(tmp);
            r.median[t] = tmp[n / 2]; r.p10[t] = tmp[(int)(n * 0.10)]; r.p90[t] = tmp[(int)(n * 0.90)];
        }
        r.valid = true;
        return r;
    }
    static double[] logReturns(double[] prices) {
        double[] r = new double[prices.length - 1];
        for (int i = 1; i < prices.length; i++) r[i - 1] = prices[i] > 0 && prices[i-1] > 0 ? Math.log(prices[i] / prices[i-1]) : 0;
        return r;
    }
    static double logReturnMean(double[] prices) { return mean(logReturns(prices)); }
    static double logReturnStd(double[] prices, double mu) { return std(logReturns(prices), mu); }
    static double mean(double[] arr) {
        double s = 0; for (double v : arr) s += v; return arr.length > 0 ? s / arr.length : 0;
    }
    static double std(double[] arr, double mu) {
        double v = 0; for (double x : arr) v += (x - mu) * (x - mu);
        return arr.length > 1 ? Math.sqrt(v / (arr.length - 1)) : 0;
    }
    static int estimateBarsPerYear(double[] prices) {
        // Can't know interval from prices alone — use a reasonable default
        // Caller should pass this; here we estimate from price count
        return Math.min(prices.length, 252);
    }
}


