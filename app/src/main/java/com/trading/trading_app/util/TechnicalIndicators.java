package com.trading.trading_app.util;

import com.trading.trading_app.model.AppConfig;
import com.trading.trading_app.model.AppConfig.ProfileParams;
import com.trading.trading_app.model.Asset;
import com.trading.trading_app.model.Asset.OhlcBar;
import java.util.HashMap; import java.util.List; import java.util.Locale; import java.util.Map;
/**Pure-Java port of the indicator computation and signal scoring from trading_backtest_script.py → compute_indicators() + generate_signals() + _score_signal().
 * Runs entirely on-device: no network call needed after OHLCV data is downloaded. All computations use primitive double[] to avoid boxing overhead.*/
public final class TechnicalIndicators {
    private TechnicalIndicators() {}
    public static class BayesWeights {
        public final Map<String, Double> buyWeights  = new HashMap<>(), sellWeights = new HashMap<>();
        public final Map<String, Double> buyHitRates  = new HashMap<>(), sellHitRates = new HashMap<>();
        public double buyThreshold, sellThreshold, baseUp, baseDn;
        public int nObs;
        public boolean valid = false;   // true when enough data was available
    }
    private static final int FORWARD  = 1;   // bars ahead to measure outcome
    private static final int MIN_OBS  = 30;  // minimum fires per indicator
    private static final String[] IND_KEYS = {"ma", "rsi", "bb", "macd", "atr"};
    public static BayesWeights computeBayesianWeights( double[] close, double[] emaShort, double[] emaLong, double[] rsi, double[] bbPct, double[] macdLine, double[] macdSig, double[] smaShort, ProfileParams p) {
        BayesWeights bw = new BayesWeights(); int n = close.length; if (n < MIN_OBS + FORWARD + 5) return bw;   // not enough data
        double rsiMid = (p.rsiOB + p.rsiOS) / 2.0;
        // Forward return: did price go up / down in next FORWARD bars?
        // upNext[i] = 1.0 if close[i+FORWARD] > close[i], else 0.0, dnNext[i] = 1.0 if close[i+FORWARD] < close[i], else 0.0
        double[] upNext = new double[n], dnNext = new double[n];
        int validObs = 0;
        double sumUp = 0, sumDn = 0;
        for (int i = 0; i < n - FORWARD; i++) {
            if (close[i] <= 0) continue;
            double fwdRet = (close[i + FORWARD] - close[i]) / close[i];
            upNext[i] = fwdRet > 0 ? 1.0 : 0.0; dnNext[i] = fwdRet < 0 ? 1.0 : 0.0;
            sumUp += upNext[i]; sumDn += dnNext[i];
            validObs++;
        }
        if (validObs < MIN_OBS) return bw;
        // Base rates (unconditional probability)
        double baseUp = Math.max(sumUp / validObs, 0.01), baseDn = Math.max(sumDn / validObs, 0.01);
        bw.baseUp = baseUp; bw.baseDn = baseDn; bw.nObs = validObs;
        // Build condition arrays (same as Python buy_conds / sell_conds)
        double[] cBuyMA = new double[n], cBuyRSI = new double[n], cBuyBB = new double[n], cBuyMACD = new double[n], cBuyATR = new double[n]; // buy conditions: fires when indicator is bullish
        double[] cSellMA = new double[n], cSellRSI = new double[n], cSellBB = new double[n], cSellMACD = new double[n], cSellATR = new double[n]; // sell conditions: fires when indicator is bearish
        for (int i = 0; i < n - FORWARD; i++) {
            cBuyMA[i] = (!Double.isNaN(emaShort[i]) && !Double.isNaN(emaLong[i]) && emaShort[i] > emaLong[i]) ? 1.0 : 0.0;
            cBuyRSI[i] = (!Double.isNaN(rsi[i]) && rsi[i] < rsiMid) ? 1.0 : 0.0;
            cBuyBB[i] = (!Double.isNaN(bbPct[i]) && bbPct[i] < 0.4) ? 1.0 : 0.0;
            cBuyMACD[i] = (!Double.isNaN(macdLine[i]) && !Double.isNaN(macdSig[i]) && macdLine[i] > macdSig[i]) ? 1.0 : 0.0;
            cBuyATR[i] = (!Double.isNaN(smaShort[i]) && close[i] > smaShort[i]) ? 1.0 : 0.0;
            cSellMA[i] = 1.0 - cBuyMA[i];
            cSellRSI[i] = (!Double.isNaN(rsi[i]) && rsi[i] > rsiMid) ? 1.0 : 0.0;
            cSellBB[i] = (!Double.isNaN(bbPct[i]) && bbPct[i] > 0.6) ? 1.0 : 0.0;
            cSellMACD[i] = 1.0 - cBuyMACD[i];
            cSellATR[i]  = 1.0 - cBuyATR[i];
        }
        double[][] buyConds = {cBuyMA,  cBuyRSI,  cBuyBB,  cBuyMACD,  cBuyATR}, sellConds = {cSellMA, cSellRSI, cSellBB, cSellMACD, cSellATR};
        // Compute log-odds weight for each indicator
        for (int k = 0; k < IND_KEYS.length; k++) {
            double[] bc = buyConds[k], sc = sellConds[k];
            String key = IND_KEYS[k];
            double[] buyW = logOddsWeight(bc, upNext, baseUp, n - FORWARD), sellW = logOddsWeight(sc, dnNext, baseDn, n - FORWARD);
            bw.buyWeights.put(key, buyW[0]); bw.buyHitRates.put(key, buyW[1]); bw.sellWeights.put(key, sellW[0]); bw.sellHitRates.put(key, sellW[1]);
        }
        // Thresholds: mean_weight × 3  (same as Python)
        double sumBuy = 0, sumSell = 0;
        for (String k : IND_KEYS) {
            sumBuy += bw.buyWeights.get(k); sumSell += bw.sellWeights.get(k);
        }
        bw.buyThreshold  = (sumBuy / IND_KEYS.length) * 3.0; bw.sellThreshold = (sumSell / IND_KEYS.length) * 3.0;
        bw.valid = true;
        return bw;
    }
    private static double[] logOddsWeight(double[] cond, double[] outcome, double baseRate, int len) {
        int nFired = 0; double sumOutcome = 0;
        for (int i = 0; i < len; i++) {
            if (cond[i] == 1.0) { nFired++; sumOutcome += outcome[i]; }
        }
        if (nFired < MIN_OBS) return new double[]{1.0, baseRate}; // Fallback: neutral weight, base hit rate
        double pOutcomeGivenFired = Math.max(sumOutcome / nFired, 0.01);
        double rawLogOdds = Math.log(pOutcomeGivenFired / baseRate);
        double clipped = Math.max(-2.0, Math.min(2.0, rawLogOdds));
        double weight = clipped + 2.0;   // shift to [0, 4]
        return new double[]{weight, pOutcomeGivenFired};
    }

    //  Entry point: given raw OHLCV bars + profile, fills all indicator fields on each bar and returns a populated Asset snapshot.
    public static Asset compute(String assetName, String ticker, List<OhlcBar> bars, ProfileParams p, double capitalUsd) {
        int n = bars.size(); if (n < 5) return null; // Increased minimum slightly for safety
        double[] open = new double[n], high = new double[n], low = new double[n], close = new double[n], vol = new double[n]; // Extract price arrays
        for (int i = 0; i < n; i++) {
            OhlcBar b = bars.get(i);
            open[i] = b.open; high[i] = b.high; low[i] = b.low; close[i] = b.close; vol[i] = (double) b.volume;
        }
        double[] emaShort = ema(close, p.maShort), emaLong  = ema(close, p.maLong), smaShort = sma(close, p.maShort); //Moving Averages
        double[] rsi = rsi(close, p.rsiPeriod); // RSI
        double[][] bb = bollingerBands(close, p.bbPeriod, p.bbStd); // Bollinger Bands
        double[] bbMid = bb[0], bbUpper = bb[1], bbLower = bb[2], bbPct = bb[3];
        double[][] macd = macd(close, p.macdFast, p.macdSlow, p.macdSignal); // MACD
        double[] macdLine = macd[0], macdSig  = macd[1], macdHist = macd[2];
        double[] atr = atr(high, low, close, p.atrPeriod); // ATR
        // Write back indicator values into bar objects
        for (int i = 0; i < n; i++) {
            OhlcBar b = bars.get(i);
            b.emaShort = (float)emaShort[i]; b.emaLong = (float)emaLong[i]; b.bbUpper = (float)bbUpper[i]; b.bbLower = (float)bbLower[i]; b.rsi = (float)rsi[i]; b.macd = (float)macdLine[i]; b.macdSig = (float)macdSig[i]; b.macdHist = (float)macdHist[i]; b.atr = (float)atr[i];
        }
        generateSignals(bars, emaShort, emaLong, rsi, bbPct, macdLine, macdSig, smaShort, p); // Generate BUY/SELL signals on each bar
        BayesWeights bw = computeBayesianWeights(close, emaShort, emaLong, rsi, bbPct, macdLine, macdSig, smaShort, p);
        // Last-bar snapshot for the asset card
        int last = n - 1, prev = Math.max(0, n - 2);
        double price = close[last], prevPrice = close[prev];
        double change = prevPrice > 0 ? (price - prevPrice) / prevPrice * 100 : 0;
        double rsiMid = (p.rsiOB + p.rsiOS) / 2.0;
        // Boolean conditions for the last bar (same logic as Python)
        boolean condMA = emaShort[last] > emaLong[last];
        boolean condRSI = !Double.isNaN(rsi[last]) && rsi[last] < rsiMid;
        boolean condBB = !Double.isNaN(bbPct[last]) && bbPct[last] < 0.4;
        boolean condMACD = !Double.isNaN(macdLine[last]) && macdLine[last] > macdSig[last];
        boolean condATR = !Double.isNaN(smaShort[last]) && price > smaShort[last];
        // Sell conditions for the last bar
        boolean sCondMA = !condMA;
        boolean sCondRSI = !Double.isNaN(rsi[last]) && rsi[last] > rsiMid;
        boolean sCondBB = !Double.isNaN(bbPct[last]) && bbPct[last] > 0.6;
        boolean sCondMACD = !condMACD;
        boolean sCondATR = !condATR;
        // Legacy equal-weight score (0-5) — kept for score bar UI
        int buyScore = (condMA?1:0) + (condRSI?1:0) + (condBB?1:0) + (condMACD?1:0) + (condATR?1:0);
        int sellScore = 5 - buyScore;
        // Bayesian weighted score
        double bayesBuyScore, bayesSellScore;
        String signal;
        if (bw.valid) {
            bayesBuyScore = (condMA ? bw.buyWeights.get("ma") : 0) + (condRSI ? bw.buyWeights.get("rsi") : 0) + (condBB ? bw.buyWeights.get("bb") : 0) + (condMACD ? bw.buyWeights.get("macd") : 0) + (condATR ? bw.buyWeights.get("atr") : 0);
            bayesSellScore = (sCondMA ? bw.sellWeights.get("ma") : 0) + (sCondRSI ? bw.sellWeights.get("rsi") : 0) + (sCondBB ? bw.sellWeights.get("bb") : 0) + (sCondMACD ? bw.sellWeights.get("macd") : 0) + (sCondATR ? bw.sellWeights.get("atr") : 0);
            // Signal: same threshold logic as Python
            if (bayesBuyScore >= bw.buyThreshold) signal = "BUY"; else if (bayesSellScore >= bw.sellThreshold) signal = "SELL"; else signal = "NEU";
        } else {
            // Fallback when < 30 observations per indicator (very short history)
            bayesBuyScore  = buyScore; bayesSellScore = sellScore;
            signal = buyScore >= 3 ? "BUY" : sellScore >= 3 ? "SELL" : "NEU";
        }
        // Order levels
        double buf = 0.005;
        double buyLimit = bbLower[last] > 0 ? bbLower[last] * (1 + buf) : price * 0.98;
        double riskPer = p.atrSlMult * atr[last];
        if (riskPer <= 0) riskPer = price * 0.02; // Fallback 2% risk if ATR is 0
        double stopLoss = buyLimit - riskPer;
        double tp1 = buyLimit + riskPer;
        double qty = buyLimit > 0 ? (capitalUsd * 0.95) / buyLimit : 0;
        double riskUsd = riskPer * qty;
        // Speed & Volume metrics
        int rocP = Math.min(10, n - 1);
        double roc = (n > rocP && close[n - rocP - 1] > 0) ? (close[last] - close[n - rocP - 1]) / close[n - rocP - 1] * 100 : 0;
        int atrPrevIdx = Math.max(0, n - 11);
        double atrChg = (n > 11 && atr[atrPrevIdx] > 0) ? (atr[last] - atr[atrPrevIdx]) / atr[atrPrevIdx] * 100 : 0;
        double bodyNow = Math.abs(close[last] - open[last]);
        double bodyAvg = meanAbs(open, close, Math.max(0, n - 20), n);
        double bodyRatio = bodyAvg > 0 ? bodyNow / bodyAvg : 1.0;
        double volNow = vol[last];
        double volAvg = mean(vol, Math.max(0, n - 20), n);
        double volRatio = volAvg > 0 ? volNow / volAvg : 1.0;
        String obvSignal = computeObvSignal(close, vol, change, n);
        // Build Asset object
        Asset asset = new Asset();
        asset.name = assetName; asset.ticker = ticker; asset.profile = AppConfig.ASSET_PROFILES.getOrDefault(assetName, "TECH");
        asset.price = price; asset.priceChange = change; asset.signal = signal;
        asset.buyScore = buyScore; asset.sellScore = sellScore; asset.bayesBuyScore = bayesBuyScore; asset.bayesSellScore = bayesSellScore;
        // Convenience: normalised 0-100 probability for sorting & display badge, we derive this from the score relative to the max possible (5 × 4.0 = 20)
        double maxScore = IND_KEYS.length * 4.0;   // 20.0
        asset.bayesBuyProb = Math.min(1.0, bayesBuyScore / maxScore); asset.bayesSellProb = Math.min(1.0, bayesSellScore / maxScore);
        // Hit rates per indicator — useful for the detail screen tooltip
        if (bw.valid) {
            asset.bayesHitRates = new HashMap<>();
            for (String k : IND_KEYS) { asset.bayesHitRates.put("buy_" + k, bw.buyHitRates.get(k)); asset.bayesHitRates.put("sell_" + k, bw.sellHitRates.get(k)); }
            asset.bayesThresholdBuy = bw.buyThreshold; asset.bayesThresholdSell = bw.sellThreshold;
        }
        asset.condMA = condMA; asset.condRSI = condRSI; asset.condBB = condBB; asset.condMACD = condMACD; asset.condATR = condATR;
        asset.rsi = rsi[last]; asset.rsiMid = rsiMid; asset.bbPct = bbPct[last]; asset.bbUpper = bbUpper[last];
        asset.bbLower = bbLower[last]; asset.bbMid = bbMid[last]; asset.macd = macdLine[last]; asset.macdSig = macdSig[last]; asset.macdHist = macdHist[last];
        asset.emaShort = emaShort[last]; asset.emaLong = emaLong[last]; asset.smaShort = smaShort[last]; asset.atr = atr[last];
        asset.buyLimit = buyLimit; asset.stopLoss = stopLoss; asset.takeProfit1 = tp1; asset.sellTarget = bbUpper[last]; asset.riskUsd = riskUsd;
        asset.roc = roc; asset.atrChg = atrChg; asset.bodyRatio = bodyRatio; asset.volRatio = volRatio; asset.volNow = volNow; asset.volAvg = volAvg;
        asset.obvSignal = obvSignal;
        asset.maShort = p.maShort; asset.maLong = p.maLong;
        asset.rsiOB = p.rsiOB;    asset.rsiOS  = p.rsiOS;
        asset.ohlcHistory = bars;
        asset.fetchedAt   = System.currentTimeMillis();

        return asset;
    }
    //  Signal generation (mirrors generate_signals in Python)
    private static void generateSignals(List<OhlcBar> bars, double[] emaShort, double[] emaLong, double[] rsi, double[] bbPct, double[] macd, double[] macdSig, double[] smaShort, ProfileParams p) {
        int n = bars.size();
        for (int i = 1; i < n; i++) {
            boolean ma = emaShort[i] > emaLong[i] && emaShort[i-1] <= emaLong[i-1], rsiB = rsi[i] < p.rsiOS, bbB = bbPct[i] < 0.2, macdB = macd[i] > macdSig[i] && macd[i-1] <= macdSig[i-1];
            boolean maS = emaShort[i] < emaLong[i] && emaShort[i-1] >= emaLong[i-1], rsiS = rsi[i] > p.rsiOB, bbS  = bbPct[i] > 0.8, macdS= macd[i] < macdSig[i] && macd[i-1] >= macdSig[i-1];
            int buyCount = (ma?1:0)+(rsiB?1:0)+(bbB?1:0)+(macdB?1:0), sellCount = (maS?1:0)+(rsiS?1:0)+(bbS?1:0)+(macdS?1:0);
            if (buyCount  >= 2) bars.get(i).signalFlag =  1; else if (sellCount >= 2) bars.get(i).signalFlag = -1; else bars.get(i).signalFlag =  0;
        }
    }
    //  Indicator math (all O(n), no external libs)
    /** Exponential Moving Average */
    public static double[] ema(double[] src, int period) {
        int n = src.length; double[] out = new double[n]; double k = 2.0 / (period + 1);
        out[0] = src[0];
        for (int i = 1; i < n; i++) out[i] = src[i] * k + out[i-1] * (1 - k);
        return out;
    }
    /** Simple Moving Average */
    public static double[] sma(double[] src, int period) {
        int n = src.length; double[] out = new double[n]; double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += src[i];
            if (i >= period) sum -= src[i - period];
            out[i] = i >= period - 1 ? sum / Math.min(i + 1, period) : Double.NaN;
        }
        return out;
    }
    /** RSI — Wilder smoothing (same as pandas ewm used in Python) */
    public static double[] rsi(double[] src, int period) {
        int n = src.length; double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = Double.NaN;
        if (n <= period) return out;
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double d = src[i] - src[i-1];
            if (d > 0) avgGain += d; else avgLoss -= d;
        }
        avgGain /= period; avgLoss /= period;
        out[period] = avgLoss == 0 ? 100 : 100 - 100.0 / (1 + avgGain / avgLoss);
        for (int i = period + 1; i < n; i++) {
            double d = src[i] - src[i-1];
            double gain = d > 0 ? d : 0, loss = d < 0 ? -d : 0;
            avgGain = (avgGain * (period - 1) + gain) / period; avgLoss = (avgLoss * (period - 1) + loss) / period;
            out[i] = avgLoss == 0 ? 100 : 100 - 100.0 / (1 + avgGain / avgLoss);
        }
        return out;
    }
    /** Bollinger Bands → [mid, upper, lower, pct] */
    public static double[][] bollingerBands(double[] src, int period, double std) {
        int n = src.length;
        double[] mid = sma(src, period), upper = new double[n], lower = new double[n], pct = new double[n];
        for (int i = 0; i < n; i++) { upper[i] = Double.NaN; lower[i] = Double.NaN; pct[i] = Double.NaN; }
        for (int i = period - 1; i < n; i++) {
            if (Double.isNaN(mid[i])) continue;
            double mean = mid[i]; double var = 0;
            for (int j = i - period + 1; j <= i; j++) var += (src[j]-mean)*(src[j]-mean);
            double s = Math.sqrt(var / period);
            upper[i] = mean + std * s; lower[i] = mean - std * s;
            double range = upper[i] - lower[i];
            pct[i] = range > 0 ? (src[i] - lower[i]) / range : 0.5;
        }
        return new double[][]{mid, upper, lower, pct};
    }
    /** MACD → [macd, signal, histogram] */
    public static double[][] macd(double[] src, int fast, int slow, int signal) {
        double[] emaFast = ema(src, fast), emaSlow  = ema(src, slow);
        int n = src.length;
        double[] macdLine = new double[n];
        for (int i = 0; i < n; i++) macdLine[i] = emaFast[i] - emaSlow[i];
        double[] sigLine = ema(macdLine, signal), hist = new double[n];
        for (int i = 0; i < n; i++) hist[i] = macdLine[i] - sigLine[i];
        return new double[][]{macdLine, sigLine, hist};
    }
    /** Average True Range */
    public static double[] atr(double[] high, double[] low, double[] close, int period) {
        int n = high.length; double[] tr  = new double[n], out = new double[n];
        for (int i = 0; i < n; i++) out[i] = 0; // TR can be 0, but ATR smoothing needs base
        for (int i = 1; i < n; i++) {
            double hl = high[i] - low[i];
            double hc = Math.abs(high[i] - close[i-1]), lc = Math.abs(low[i] - close[i-1]);
            tr[i] = Math.max(hl, Math.max(hc, lc));
        }
        // Wilder smoothing
        if (n <= period) return out;
        double sum = 0;
        for (int i = 1; i <= period; i++) sum += tr[i];
        out[period] = sum / period;
        for (int i = period + 1; i < n; i++) out[i] = (out[i-1] * (period - 1) + tr[i]) / period;
        return out;
    }
    // Helpers
    private static String computeObvSignal(double[] close, double[] vol, double change, int n) {
        if (n < 10) return "N/A";
        double obv = 0, obvStart = 0;
        for (int i = 1; i < n; i++) {
            obv += Math.signum(close[i] - close[i-1]) * vol[i];
            if (i == n - 6) obvStart = obv;
        }
        double obvDir = obv - obvStart;
        if (obvDir > 0 && change >= 0) return "BULLISH"; else if (obvDir < 0 && change <  0) return "BEARISH";
        else if (obvDir > 0 && change <  0) return "DIVERGENCE ▲"; else return "DIVERGENCE ▼";
    }
    private static double mean(double[] arr, int from, int to) {
        double s = 0; int cnt = to - from; if (cnt <= 0) return 0;
        for (int i = from; i < to; i++) s += arr[i];
        return s / cnt;
    }
    private static double meanAbs(double[] open, double[] close, int from, int to) {
        double s = 0; int cnt = to - from; if (cnt <= 0) return 0;
        for (int i = from; i < to; i++) s += Math.abs(close[i] - open[i]);
        return s / cnt;
    }
    /** Pearson correlation between two equal-length arrays (for Correlation Matrix screen). */
    public static double pearsonCorr(double[] x, double[] y) {
        int n = x.length; if (n < 2) return 0;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
        mx /= n; my /= n;
        double num = 0, dx2 = 0, dy2 = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i]-mx, dy = y[i]-my;
            num += dx*dy; dx2 += dx*dx; dy2 += dy*dy;
        }
        double denom = Math.sqrt(dx2 * dy2);
        return denom == 0 ? 0 : num / denom;
    }
    /** Compute daily % returns from price array. */
    public static double[] pctReturns(double[] prices) {
        int n = prices.length; double[] ret = new double[n];
        for (int i = 1; i < n; i++) ret[i] = prices[i-1] > 0 ? (prices[i] - prices[i-1]) / prices[i-1] * 100 : 0;
        return ret;
    }
}