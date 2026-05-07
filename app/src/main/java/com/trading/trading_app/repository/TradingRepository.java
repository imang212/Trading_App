package com.trading.trading_app.repository;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData; import androidx.lifecycle.MutableLiveData;
import com.trading.trading_app.api.ApiClient;
import com.trading.trading_app.api.YahooChartResponse;
import com.trading.trading_app.api.YahooChartResponse.ChartResult;
import com.trading.trading_app.api.YahooChartResponse.Quote;
import com.trading.trading_app.model.AppConfig;
import com.trading.trading_app.model.AppConfig.ProfileParams;
import com.trading.trading_app.model.Asset;
import com.trading.trading_app.model.Asset.OhlcBar;
import com.trading.trading_app.model.BacktestResult;
import com.trading.trading_app.util.TechnicalIndicators;
import java.util.ArrayList; import java.util.LinkedHashMap; import java.util.List; import java.util.Map;
import java.util.concurrent.ExecutorService; import java.util.concurrent.Executors; import java.util.concurrent.atomic.AtomicInteger;
import retrofit2.Call; import retrofit2.Callback; import retrofit2.Response;
/**Single source of truth for all market data. Calls technical indicators for signals count.
 * Architecture:
 *  - Retrofit downloads raw OHLCV from Yahoo Finance (background thread).
 *  - TechnicalIndicators computes all indicators on the raw data.
 *  - Results are posted to LiveData observed by ViewModels.
 *  - An in-memory cache avoids re-downloads within the TTL window.*/
public class TradingRepository {
    private static final String TAG  = "TradingRepo";
    private static final long TTL_MS = 30 * 60 * 1000L;  // 30 minutes
    private static volatile TradingRepository INSTANCE;
    private final Context appContext;
    private final ExecutorService executor;
    private volatile boolean convertCurrency = true;
    private static final Map<String, Double> FX_TO_USD = new java.util.HashMap<String, Double>() {{
        put("GBP", 1.27); put("GBX", 0.0127); put("GBp", 0.0127); // GBX/GBp = pence
        put("EUR", 1.09); put("JPY", 0.0067); put("CHF", 1.13); put("AUD", 0.65); put("CAD", 0.74);
        put("HKD", 0.128); put("CNY", 0.138); put("DKK", 0.14); put("CZK", 0.043);
    }};
    private static final Map<String, String> CURRENCY_SYMBOLS = new java.util.HashMap<String, String>() {{
        put("USD", "$"); put("EUR", "€"); put("GBP", "£"); put("GBX", "p"); put("GBp", "p");
        put("JPY", "¥"); put("CHF", "Fr"); put("AUD", "A$"); put("CAD", "C$");
        put("HKD", "HK$"); put("CNY", "¥"); put("DKK", "kr"); put("CZK", "Kč");
    }};
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(); // In-memory cache: assetName → (timestamp, Asset)
    private final MutableLiveData<List<Asset>> signalsLd = new MutableLiveData<>(); // Live data streams
    private final MutableLiveData<String> errorLd = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLd = new MutableLiveData<>(false);
    private final MutableLiveData<List<BacktestResult>> backtestLd = new MutableLiveData<>();
    private TradingRepository(Context ctx) {
        this.appContext = ctx.getApplicationContext();
        this.executor = Executors.newFixedThreadPool(4); // Thread pool: 4 threads → 4 assets fetch in parallel
    }
    public static TradingRepository getInstance(Context ctx) {
        if (INSTANCE == null) synchronized (TradingRepository.class) {
            if (INSTANCE == null) INSTANCE = new TradingRepository(ctx);
        }
        return INSTANCE;
    }
    public void setConvertCurrency(boolean convert) { this.convertCurrency = convert; }
    // Public LiveData
    public LiveData<List<Asset>> getSignals() { return signalsLd; }
    public LiveData<String> getError() { return errorLd; }
    public LiveData<Boolean> isLoading() { return loadingLd; }
    public LiveData<List<BacktestResult>> getBacktest() { return backtestLd; }
    //  Fetch signals for all assets (or a subset) at a given interval
    public void fetchSignals(String interval, double capital, List<String> assetNames) {
        loadingLd.postValue(true); errorLd.postValue(null);
        List<String> toFetch = assetNames != null ? assetNames : new ArrayList<>(AppConfig.ASSETS.keySet());
        String period = AppConfig.Interval.fromYf(interval).period;
        AtomicInteger remaining = new AtomicInteger(toFetch.size());
        List<Asset> results = new ArrayList<>();
        Object lock = new Object();
        for (String name : toFetch) {
            String ticker = AppConfig.ASSETS.get(name);
            String profile = AppConfig.ASSET_PROFILES.getOrDefault(name, "TECH");
            ProfileParams p = AppConfig.PROFILES.get(profile);
            if (ticker == null || p == null) { decrement(remaining, results, lock); continue; }
            // Return from cache if fresh enough
            CacheEntry cached;
            synchronized (cache) { cached = cache.get(name + "_" + interval); }
            if (cached != null && System.currentTimeMillis() - cached.timestamp < TTL_MS) {
                synchronized (lock) { results.add(cached.asset); }
                decrement(remaining, results, lock);
                continue;
            }
            // Build request — Yahoo uses 4h as 1h resample like the Python script
            String fetchInterval = "4h".equals(interval) ? "1h" : interval;
            ApiClient.get(appContext).getChart(ticker, fetchInterval, period, false).enqueue(new Callback<YahooChartResponse>() {
                    @Override
                    public void onResponse(Call<YahooChartResponse> call, Response<YahooChartResponse> response) {
                        executor.submit(() -> {
                            try {
                                Asset asset = parseAndCompute(name, ticker, interval, response, p, capital);
                                if (asset != null) {
                                    synchronized (cache) { cache.put(name + "_" + interval, new CacheEntry(asset, System.currentTimeMillis())); }
                                    synchronized (lock) { results.add(asset); }
                                }
                            }
                            catch (Exception e) { Log.w(TAG, "Parse error " + name + ": " + e.getMessage(), e); }
                            finally { decrement(remaining, results, lock); }
                        });
                    }
                    @Override
                    public void onFailure(Call<YahooChartResponse> call, Throwable t) {
                        Log.w(TAG, "Network error " + name + ": " + t.getMessage());
                        // Try fallback URL
                        ApiClient.getFallback(appContext).getChart(ticker, fetchInterval, period, false).enqueue(new Callback<YahooChartResponse>() {
                                @Override public void onResponse(Call<YahooChartResponse> c, Response<YahooChartResponse> r) {
                                    executor.submit(() -> {
                                        try {
                                            Asset asset = parseAndCompute(name, ticker, interval, r, p, capital);
                                            if (asset != null) {
                                                synchronized (cache) { cache.put(name + "_" + interval, new CacheEntry(asset, System.currentTimeMillis())); }
                                                synchronized (lock) { results.add(asset); }
                                            }
                                        } catch (Exception e) { /* silent */ }
                                        finally { decrement(remaining, results, lock); }
                                    });
                                }
                                @Override public void onFailure(Call<YahooChartResponse> c, Throwable t2) { decrement(remaining, results, lock); }
                            });
                    }
                });
        }
    }
    //  Parse OHLCV + run indicator computation
    private Asset parseAndCompute(String name, String ticker, String interval, Response<YahooChartResponse> response, ProfileParams p, double capital) {
        if (!response.isSuccessful()) { Log.w(TAG, "API Error " + name + ": Code " + response.code()); return null; }
        YahooChartResponse body = response.body();
        if (body == null) { Log.w(TAG, "Empty body for " + name); return null; }
        if (body.hasError()) { Log.w(TAG, "Yahoo Error for " + name); return null; }
        ChartResult result = body.firstResult();
        if (result == null || result.timestamp == null || result.timestamp.isEmpty()) { Log.w(TAG, "No results/timestamp for " + name); return null; }

        String currency = (result.meta != null) ? result.meta.currency : "USD";

        List<Long> ts = result.timestamp;
        if (result.indicators == null || result.indicators.quote == null || result.indicators.quote.isEmpty()) { Log.w(TAG, "No quote data for " + name); return null; }
        Quote quote = result.indicators.quote.get(0);
        int n = ts.size();
        List<OhlcBar> bars = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Double o = safeGet(quote.open, i), h = safeGet(quote.high, i), l = safeGet(quote.low, i), c = safeGet(quote.close, i);
            if (o == null || h == null || l == null || c == null || c <= 0) continue;
            OhlcBar bar = new OhlcBar();
            bar.timestamp = ts.get(i) * 1000L;   // seconds → ms
            bar.open = o.floatValue(); bar.high = h.floatValue(); bar.low = l.floatValue(); bar.close = c.floatValue();
            bar.volume = (quote.volume != null && i < quote.volume.size() && quote.volume.get(i) != null) ? quote.volume.get(i) : 0L;
            bars.add(bar);
        }
        // Resample to 4h if needed (mirror Python logic)
        if ("4h".equals(interval)) bars = resample4h(bars);
        int minBars = Math.max(p.maLong + 1, 30); // Slightly more relaxed but still needs enough for MA
        if (bars.size() < minBars) {
            Log.w(TAG, "Not enough bars for " + name + ": " + bars.size() + " < " + minBars);
            return null;
        }
        Asset asset = TechnicalIndicators.compute(name, ticker, bars, p, capital);
        if (asset != null) {
            asset.currencySymbol = CURRENCY_SYMBOLS.getOrDefault(currency, "$");
            if (convertCurrency && !"USD".equals(currency)) {
                Double rate = FX_TO_USD.get(currency);
                if (rate != null) {
                    float r = rate.floatValue();
                    asset.price *= r; asset.buyLimit *= r; asset.stopLoss *= r; asset.takeProfit1 *= r; asset.sellTarget *= r; asset.riskUsd *= r; asset.atr *= r;
                    asset.ticker = ticker + " (" + currency + "→USD)";
                    asset.currencySymbol = "$";
                    for (OhlcBar b : bars) {
                        b.open *= r; b.high *= r; b.low *= r; b.close *= r;
                        b.emaShort *= r; b.emaLong *= r; b.bbUpper *= r; b.bbLower *= r;
                        b.macd *= r; b.macdSig *= r; b.macdHist *= r; b.atr *= r;
                    }
                }
            }
        }
        return asset;
    }
    /** Resample 1h bars into 4h bars (OHLCV aggregation). */
    private List<OhlcBar> resample4h(List<OhlcBar> h1bars) {
        List<OhlcBar> out = new ArrayList<>();
        if (h1bars.isEmpty()) return out;
        long bucketMs = 4 * 3600 * 1000L;
        OhlcBar cur = null;
        int count = 0;
        for (OhlcBar b : h1bars) {
            long bucket = (b.timestamp / bucketMs) * bucketMs;
            if (cur == null || cur.timestamp != bucket) {
                if (cur != null) out.add(cur);
                cur = new OhlcBar();
                cur.timestamp = bucket;
                cur.open = b.open; cur.high = b.high; cur.low = b.low; cur.close = b.close; cur.volume = b.volume;
            } else {
                cur.high = Math.max(cur.high, b.high); cur.low = Math.min(cur.low, b.low); cur.close = b.close; cur.volume += b.volume;
            }
        }
        if (cur != null) out.add(cur);
        return out;
    }
    //  Fetch single asset (for detail screen)
    public void fetchAsset(String name, String interval, double capital, AssetCallback callback) {
        String ticker = AppConfig.ASSETS.get(name);
        String profile = AppConfig.ASSET_PROFILES.getOrDefault(name, "TECH");
        ProfileParams p = AppConfig.PROFILES.get(profile);
        if (ticker == null || p == null) { callback.onError("Unknown asset"); return; }
        String period = AppConfig.Interval.fromYf(interval).period;
        String fi = "4h".equals(interval) ? "1h" : interval;
        ApiClient.get(appContext).getChart(ticker, fi, period, false).enqueue(new Callback<YahooChartResponse>() {
                @Override public void onResponse(Call<YahooChartResponse> call, Response<YahooChartResponse> response) {
                    executor.submit(() -> {
                        Asset a = parseAndCompute(name, ticker, interval, response, p, capital);
                        if (a != null) callback.onSuccess(a);
                        else callback.onError("No data for " + name);
                    });
                }
                @Override public void onFailure(Call<YahooChartResponse> call, Throwable t) { callback.onError(t.getMessage()); }
            });
    }
    public interface AssetCallback { void onSuccess(Asset asset); void onError(String message); }
    public void runBacktest(String assetName, double capital, long startDateMs, BacktestCallback callback) {
        String ticker = AppConfig.ASSETS.get(assetName);
        String profile = AppConfig.ASSET_PROFILES.getOrDefault(assetName, "TECH");
        ProfileParams p = AppConfig.PROFILES.get(profile);
        if (ticker == null || p == null) { callback.onError("Unknown asset"); return; }
        // Use custom start date if set, otherwise default 5y period
        String btPeriod = "5y";
        if (startDateMs > 0) {
            long diffMs = System.currentTimeMillis() - startDateMs; long diffDays = diffMs / (24 * 3600 * 1000L);
            if (diffDays <= 30) btPeriod = "1mo"; else if (diffDays <= 90) btPeriod = "3mo"; else if (diffDays <= 180) btPeriod = "6mo";
            else if (diffDays <= 365) btPeriod = "1y"; else if (diffDays <= 730) btPeriod = "2y"; else if (diffDays <= 1825) btPeriod = "5y"; else btPeriod = "10y";
        }
        ApiClient.get(appContext).getChart(ticker, "1d", btPeriod, false)
                .enqueue(new Callback<YahooChartResponse>() {
                    @Override public void onResponse(Call<YahooChartResponse> call, Response<YahooChartResponse> response) {
                        executor.submit(() -> {
                            try {
                                Asset a = parseAndCompute(assetName, ticker, "1d", response, p, capital);
                                if (a == null) { callback.onError("No data"); return; }
                                BacktestResult bt = computeBacktest(a, p, capital);
                                callback.onSuccess(bt);
                            } catch (Exception e) { callback.onError(e.getMessage()); }
                        });
                    }
                    @Override public void onFailure(Call<YahooChartResponse> c, Throwable t) { callback.onError(t.getMessage()); }
                });
    }
    public interface BacktestCallback { void onSuccess(BacktestResult result); void onError(String message); }
    /** On-device backtest simulation — simple MA/RSI/BB/MACD signal strategy. */
    private BacktestResult computeBacktest(Asset asset, ProfileParams p, double capital) {
        List<OhlcBar> bars = asset.ohlcHistory;
        int n = bars.size();
        double equity = capital;
        double position = 0;
        double entryPx = 0;
        double buyHold = capital / bars.get(0).close * bars.get(n-1).close;
        double maxEq = capital;
        double maxDD = 0;
        int wins = 0, losses = 0;
        List<BacktestResult.EquityPoint> curve = new ArrayList<>();
        curve.add(new BacktestResult.EquityPoint(bars.get(0).timestamp, capital));
        for (int i = 1; i < n; i++) {
            OhlcBar bar = bars.get(i);
            float px = bar.close;
            if (bar.signalFlag == 1 && position == 0) {
                position = equity / px; entryPx  = px; equity = 0; // BUY
            } else if (bar.signalFlag == -1 && position > 0) {
                // SELL
                double proceeds = position * px * (1 - 0.001);  // 0.1% commission
                equity = proceeds;
                if (px > entryPx) wins++; else losses++;
                position = 0;
            }
            double totalEq = equity + position * px;
            if (totalEq > maxEq) maxEq = totalEq;
            double dd = maxEq > 0 ? (maxEq - totalEq) / maxEq * 100 : 0;
            if (dd > maxDD) maxDD = dd;
            curve.add(new BacktestResult.EquityPoint(bar.timestamp, totalEq));
        }
        // Close any open position at last bar price
        if (position > 0) equity += position * bars.get(n-1).close;
        int totalTrades = wins + losses;
        double ret = (equity - capital) / capital * 100;
        double bhRet = (buyHold - capital) / capital * 100;
        double sharpe = computeSharpe(curve); // Simple Sharpe from equity curve
        BacktestResult bt = new BacktestResult();
        bt.asset = asset.name; bt.profile = asset.profile;
        bt.initialCapital = capital; bt.finalValue = equity; bt.totalReturn = ret; bt.bhReturn = bhRet; bt.alpha = ret - bhRet;
        bt.winRate = totalTrades > 0 ? (double) wins / totalTrades * 100 : 0;
        bt.sharpe = sharpe; bt.maxDrawdown = maxDD;
        bt.profitFactor = losses > 0 ? (double) wins / losses : wins > 0 ? 99 : 0;
        bt.totalTrades = totalTrades; bt.winningTrades = wins; bt.losingTrades = losses; bt.equityCurve = curve;
        return bt;
    }
    private double computeSharpe(List<BacktestResult.EquityPoint> curve) {
        if (curve.size() < 2) return 0;
        double[] rets = new double[curve.size() - 1];
        for (int i = 1; i < curve.size(); i++) {
            double prev = curve.get(i-1).equity;
            rets[i-1] = prev > 0 ? (curve.get(i).equity - prev) / prev : 0;
        }
        double mean = 0;
        for (double r : rets) mean += r;
        mean /= rets.length;
        double var = 0;
        for (double r : rets) var += (r - mean) * (r - mean);
        double std = Math.sqrt(var / rets.length);
        return std > 0 ? (mean / std) * Math.sqrt(252) : 0;
    }
    // Internal helpers
    private void decrement(AtomicInteger remaining, List<Asset> results, Object lock) {
        if (remaining.decrementAndGet() == 0) {
            loadingLd.postValue(false);
            List<Asset> sorted;
            synchronized (lock) { sorted = new ArrayList<>(results); }
            // Sort: BUY first, then SELL, then NEU; within each group sort by name
            sorted.sort((a, b) -> {
                int ao = signalOrder(a.signal), bo = signalOrder(b.signal);
                return ao != bo ? Integer.compare(ao, bo) : a.name.compareTo(b.name);
            });
            signalsLd.postValue(sorted);
        }
    }
    private int signalOrder(String s) { if ("BUY".equals(s))  return 0; if ("SELL".equals(s)) return 1; return 2; }
    private static <T> T safeGet(List<T> list, int i) {
        if (list == null || i >= list.size()) return null;
        return list.get(i);
    }
    public void clearCache() { cache.clear(); }
    private static class CacheEntry { // Cache entry
        final Asset asset; final long  timestamp;
        CacheEntry(Asset asset, long ts) { this.asset = asset; this.timestamp = ts; }
    }
}