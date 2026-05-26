package com.trading.trading_app.ui.detail;

import android.graphics.Color; import android.os.Bundle;
import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup;
import androidx.annotation.NonNull; import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.github.mikephil.charting.charts.CandleStickChart; import com.github.mikephil.charting.charts.LineChart; import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis; import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.trading.app.databinding.FragmentAssetDetailBinding;
import com.trading.trading_app.model.Asset; import com.trading.trading_app.model.Asset.OhlcBar;
import com.trading.trading_app.repository.TradingRepository; import com.trading.trading_app.viewmodel.SignalViewModel;
import java.text.SimpleDateFormat;
import java.util.ArrayList; import java.util.Date; import java.util.List; import java.util.Locale;
import java.util.concurrent.ExecutorService; import java.util.concurrent.Executors;
import com.trading.trading_app.util.PredictionEngine; import com.trading.trading_app.util.PredictionEngine.PredictionResult;
import com.trading.trading_app.util.ProphetLite; import com.trading.trading_app.util.ProphetLite.ProphetResult;
/**Asset detail screen. Charts (all via MPAndroidChart):
 *   1. Candlestick chart + EMA lines + BB bands + Buy/Sell markers + Volume bars
 *   2. RSI line chart
 *   3. MACD bar + signal line chart
 *   4. ATR area chart
 * Price levels, indicator status table, speed & volume — same as Streamlit dashboard.*/
public class AssetDetailFragment extends Fragment {
    public static final String ARG_ASSET_NAME = "asset_name", ARG_INTERVAL = "interval";
    private FragmentAssetDetailBinding binding;
    private SignalViewModel vm;
    private String assetName, interval;
    private final ExecutorService predExecutor = Executors.newSingleThreadExecutor();
    private enum PredMode {MONTE_CARLO, PROPHET}
    private PredMode predMode = PredMode.MONTE_CARLO;
    private Asset currentAsset;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAssetDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(SignalViewModel.class);
        if (getArguments() != null) {
            assetName = getArguments().getString(ARG_ASSET_NAME);
            interval = getArguments().getString(ARG_INTERVAL, "1d");
        }
        requireActivity().setTitle(assetName != null ? assetName : "Detail");
        binding.progressDetail.setVisibility(View.VISIBLE);
        loadData();
    }
    // Load data
    private void loadData() {
        List<Asset> cached = vm.getAllSignals().getValue(); // Try to get from already-loaded signals first
        if (cached != null) for (Asset a : cached) if (a.name.equals(assetName)) { populateUi(a); return; }
        // Otherwise fetch individually
        TradingRepository.getInstance(requireContext()).fetchAsset(assetName, interval, vm.getCapital(), new TradingRepository.AssetCallback() {
            @Override
            public void onSuccess(Asset asset) { requireActivity().runOnUiThread(() -> populateUi(asset)); }
            @Override
            public void onError(String msg) {
                requireActivity().runOnUiThread(() -> {
                    binding.progressDetail.setVisibility(View.GONE);
                    binding.tvDetailError.setVisibility(View.VISIBLE);
                    binding.tvDetailError.setText("Error: " + msg);
                });
            }
        });
    }
    // Populate UI
    private void populateUi(Asset asset) {
        binding.progressDetail.setVisibility(View.GONE);
        binding.scrollContent.setVisibility(View.VISIBLE);
        // Header
        binding.tvDetailPrice.setText(String.format("%s%,.2f", asset.currencySymbol, asset.price));
        String chg = String.format("%+.2f%%", asset.priceChange);
        binding.tvDetailChange.setText(chg);
        binding.tvDetailChange.setTextColor(asset.priceChange >= 0 ? Color.parseColor("#2ECC71") : Color.parseColor("#E74C3C"));
        binding.tvDetailSignal.setText(asset.signal);
        binding.tvDetailSignal.setTextColor(Color.parseColor(signalHex(asset.signal)));
        binding.tvDetailScore.setText(String.format(Locale.US, "Score %d/5 | BUY %.2f/%.2f SELL %.2f/%.2f", asset.buyScore, asset.bayesBuyScore, asset.bayesThresholdBuy, asset.bayesSellScore, asset.bayesThresholdSell));
        binding.tvDetailAtr.setText(String.format("ATR %s%.2f  (%.1f%%)", asset.currencySymbol, asset.atr, asset.atr / asset.price * 100));
        // BB / RSI / MACD context badges
        binding.tvBbContext.setText(bbContext(asset.bbPct));
        binding.tvRsiContext.setText(rsiContext(asset.rsi, asset.rsiMid, asset.rsiOS, asset.rsiOB));
        binding.tvMacdContext.setText(macdContext(asset.macdHist));
        // Price levels
        binding.tvBuyLimit.setText(String.format("%s%,.2f  (%+.1f%%)", asset.currencySymbol, asset.buyLimit, asset.pctTo(asset.buyLimit)));
        binding.tvStopLoss.setText(String.format("%s%,.2f  (%+.1f%%)", asset.currencySymbol, asset.stopLoss, asset.pctTo(asset.stopLoss)));
        binding.tvTp1.setText(String.format("%s%,.2f  (%+.1f%%)  R:R 1:1", asset.currencySymbol, asset.takeProfit1, asset.pctTo(asset.takeProfit1)));
        binding.tvSellTarget.setText(String.format("%s%,.2f  (%+.1f%%)  1:%.1f", asset.currencySymbol, asset.sellTarget, asset.pctTo(asset.sellTarget), asset.rrTo(asset.sellTarget)));
        binding.tvRiskUsd.setText(String.format("%s%,.0f risk / trade", asset.currencySymbol, asset.riskUsd));
        // Indicator status table
        bindIndicatorRow(binding.rowMA.getRoot(), "MA Crossover", String.format("EMA%d %s EMA%d", asset.maShort, asset.condMA ? ">" : "<", asset.maLong), asset.condMA);
        bindIndicatorRow(binding.rowRsi.getRoot(), "RSI", String.format("%.1f", asset.rsi), asset.condRSI);
        bindIndicatorRow(binding.rowBb.getRoot(), "Bollinger", String.format("BB%% %.2f", asset.bbPct), asset.condBB);
        bindIndicatorRow(binding.rowMacd.getRoot(), "MACD", String.format("hist %.3f", asset.macdHist), asset.condMACD);
        bindIndicatorRow(binding.rowAtr.getRoot(), "ATR trend", String.format("Price %s SMA%d", asset.price > asset.smaShort ? ">" : "<", asset.maShort), asset.condATR);
        // Speed & Volume
        binding.tvRoc.setText(String.format("%+.2f%%", asset.roc));
        binding.tvAtrChg.setText(String.format("%+.1f%%", asset.atrChg));
        binding.tvBodyRatio.setText(String.format("%.1fx avg", asset.bodyRatio));
        binding.tvVolRatioDetail.setText(asset.volRatio > 0 ? String.format("%+.0f%% vs avg", (asset.volRatio - 1) * 100) : "N/A");
        binding.tvObvSignal.setText(asset.obvSignal);
        // Charts
        if (asset.ohlcHistory != null && !asset.ohlcHistory.isEmpty()) {
            setupCandleChart(asset);
            setupRsiChart(asset);
            setupMacdChart(asset);
            setupAtrChart(asset);
        }
        currentAsset = asset;
        setupPredictionTabs(asset);
        runPrediction(asset);
    }
    // Candlestick + Volume + EMA + BB
    private void setupCandleChart(Asset asset) {
        List<OhlcBar> bars = asset.ohlcHistory;
        int n = bars.size();
        List<CandleEntry> candles = new ArrayList<>(n);
        List<Entry> emaShort = new ArrayList<>(n), emaLong = new ArrayList<>(n);
        List<Entry> bbUpper = new ArrayList<>(n), bbLower = new ArrayList<>(n);
        List<BarEntry> volume = new ArrayList<>(n);
        List<Entry> buyMarkers = new ArrayList<>(), selMarkers = new ArrayList<>();
        String[] xLabels = new String[n];
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
        for (int i = 0; i < n; i++) {
            OhlcBar b = bars.get(i);
            candles.add(new CandleEntry(i, b.high, b.low, b.open, b.close));
            emaShort.add(new Entry(i, b.emaShort));
            emaLong.add(new Entry(i, b.emaLong));
            bbUpper.add(new Entry(i, b.bbUpper));
            bbLower.add(new Entry(i, b.bbLower));
            volume.add(new BarEntry(i, b.volume));
            xLabels[i] = sdf.format(new Date(b.timestamp));
            if (b.signalFlag == 1) buyMarkers.add(new Entry(i, b.low * 0.995f));
            if (b.signalFlag == -1) selMarkers.add(new Entry(i, b.high * 1.005f));
        }
        // Candles dataset
        CandleDataSet cds = new CandleDataSet(candles, "Price");
        cds.setDecreasingColor(Color.parseColor("#E74C3C"));
        cds.setIncreasingColor(Color.parseColor("#2ECC71"));
        cds.setDecreasingPaintStyle(android.graphics.Paint.Style.FILL);
        cds.setIncreasingPaintStyle(android.graphics.Paint.Style.FILL);
        cds.setShadowColor(Color.GRAY);
        cds.setShadowWidth(0.7f);
        cds.setDrawValues(false);
        // EMA lines
        LineDataSet emsDS = lineDataSet(emaShort, "EMA" + asset.maShort, "#F39C12", 1.5f);
        LineDataSet emlDS = lineDataSet(emaLong, "EMA" + asset.maLong, "#3498DB", 1.5f);
        LineDataSet bbUDS = lineDataSet(bbUpper, "BB Upper", "#4FC3F7", 1f);
        LineDataSet bbLDS = lineDataSet(bbLower, "BB Lower", "#4FC3F7", 1f);
        bbLDS.setFillAlpha(30);
        bbLDS.setFillColor(Color.parseColor("#4FC3F7"));
        bbLDS.setDrawFilled(true);
        // Buy/Sell markers
        LineDataSet buyDS = markerSet(buyMarkers, "BUY", "#2ECC71", true);
        LineDataSet selDS = markerSet(selMarkers, "SELL", "#E74C3C", false);
        binding.candleChart.setData(new CombinedData());
        CombinedData combined = new CombinedData();
        combined.setData(new CandleData(cds));
        combined.setData(new LineData(emsDS, emlDS, bbUDS, bbLDS, buyDS, selDS));
        binding.candleChart.setData(combined);
        styleChart(binding.candleChart, xLabels);
        // Horizontal price level lines
        addLimitLine(binding.candleChart.getAxisLeft(), asset.buyLimit, "#2ECC71", "Buy Limit");
        addLimitLine(binding.candleChart.getAxisLeft(), asset.stopLoss, "#E74C3C", "Stop");
        addLimitLine(binding.candleChart.getAxisLeft(), asset.takeProfit1, "#3498DB", "TP1");
        binding.candleChart.invalidate();
    }
    // RSI chart
    private void setupRsiChart(Asset asset) {
        List<OhlcBar> bars = asset.ohlcHistory;
        int n = bars.size();
        List<Entry> rsiEntries = new ArrayList<>(n);
        String[] xl = new String[n];
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
        for (int i = 0; i < n; i++) {
            rsiEntries.add(new Entry(i, bars.get(i).rsi));
            xl[i] = sdf.format(new Date(bars.get(i).timestamp));
        }
        LineDataSet ds = lineDataSet(rsiEntries, "RSI", "#E67E22", 1.8f);
        binding.rsiChart.setData(new LineData(ds));
        styleChart(binding.rsiChart, xl);
        addLimitLine(binding.rsiChart.getAxisLeft(), asset.rsiOB, "#E74C3C", "OB");
        addLimitLine(binding.rsiChart.getAxisLeft(), asset.rsiOS, "#2ECC71", "OS");
        addLimitLine(binding.rsiChart.getAxisLeft(), 50, "#888888", "");
        binding.rsiChart.getAxisLeft().setAxisMinimum(0);
        binding.rsiChart.getAxisLeft().setAxisMaximum(100);
        binding.rsiChart.invalidate();
    }
    // MACD chart
    private void setupMacdChart(Asset asset) {
        List<OhlcBar> bars = asset.ohlcHistory;
        int n = bars.size();
        List<BarEntry> hist = new ArrayList<>(n);
        List<Entry> macd = new ArrayList<>(n), sig = new ArrayList<>(n);
        String[] xl = new String[n];
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
        int[] histColors = new int[n];
        for (int i = 0; i < n; i++) {
            OhlcBar b = bars.get(i);
            hist.add(new BarEntry(i, b.macdHist));
            macd.add(new Entry(i, b.macd));
            sig.add(new Entry(i, b.macdSig));
            xl[i] = sdf.format(new Date(b.timestamp));
            histColors[i] = b.macdHist >= 0 ? Color.parseColor("#2ECC71") : Color.parseColor("#E74C3C");
        }
        BarDataSet histDs = new BarDataSet(hist, "Histogram");
        histDs.setColors(histColors);
        histDs.setDrawValues(false);
        LineDataSet macdDs = lineDataSet(macd, "MACD", "#378ADD", 1.5f);
        LineDataSet sigDs = lineDataSet(sig, "Signal", "#E74C3C", 1.5f);
        CombinedData cd = new CombinedData();
        cd.setData(new BarData(histDs));
        cd.setData(new LineData(macdDs, sigDs));
        binding.macdChart.setData(cd);
        styleChart(binding.macdChart, xl);
        binding.macdChart.invalidate();
    }
    // ATR chart
    private void setupAtrChart(Asset asset) {
        List<OhlcBar> bars = asset.ohlcHistory;
        int n = bars.size();
        List<Entry> atrEntries = new ArrayList<>(n);
        String[] xl = new String[n];
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
        for (int i = 0; i < n; i++) {
            atrEntries.add(new Entry(i, bars.get(i).atr));
            xl[i] = sdf.format(new Date(bars.get(i).timestamp));
        }
        LineDataSet ds = lineDataSet(atrEntries, "ATR", "#9B59B6", 1.5f);
        ds.setFillAlpha(40);
        ds.setFillColor(Color.parseColor("#9B59B6"));
        ds.setDrawFilled(true);
        binding.atrChart.setData(new LineData(ds));
        styleChart(binding.atrChart, xl);
        binding.atrChart.invalidate();
    }
    // Chart styling helpers
    private void styleChart(com.github.mikephil.charting.charts.Chart<?> chart, String[] labels) {
        chart.getDescription().setEnabled(false);
        chart.setNoDataText("Loading…");
        chart.setBackgroundColor(Color.parseColor("#1E2130"));
        chart.getLegend().setTextColor(Color.LTGRAY);
        if (chart instanceof com.github.mikephil.charting.charts.BarLineChartBase) {
            com.github.mikephil.charting.charts.BarLineChartBase<?> blChart = (com.github.mikephil.charting.charts.BarLineChartBase<?>) chart;
            blChart.setGridBackgroundColor(Color.parseColor("#1E2130"));
            blChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
            blChart.getXAxis().setTextColor(Color.LTGRAY);
            blChart.getXAxis().setGridColor(Color.parseColor("#2A2D3E"));
            blChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
            blChart.getXAxis().setLabelRotationAngle(-45);
            blChart.getAxisLeft().setTextColor(Color.LTGRAY);
            blChart.getAxisLeft().setGridColor(Color.parseColor("#2A2D3E"));
            blChart.getAxisRight().setEnabled(false);
        }
    }
    private LineDataSet lineDataSet(List<Entry> entries, String label, String hex, float width) {
        LineDataSet ds = new LineDataSet(entries, label);
        ds.setColor(Color.parseColor(hex));
        ds.setLineWidth(width);
        ds.setDrawCircles(false);
        ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.LINEAR);
        return ds;
    }
    private LineDataSet markerSet(List<Entry> entries, String label, String hex, boolean up) {
        LineDataSet ds = new LineDataSet(entries, label);
        ds.setColor(Color.parseColor(hex));
        ds.setDrawCircles(true);
        ds.setCircleColor(Color.parseColor(hex));
        ds.setCircleRadius(5f);
        ds.setDrawValues(false);
        ds.setLineWidth(0);
        return ds;
    }
    private void addLimitLine(YAxis axis, double value, String hex, String label) {
        LimitLine ll = new LimitLine((float) value, label);
        ll.setLineColor(Color.parseColor(hex));
        ll.setLineWidth(1f);
        ll.setTextColor(Color.parseColor(hex));
        ll.setTextSize(9f);
        ll.enableDashedLine(10f, 5f, 0f);
        axis.addLimitLine(ll);
    }
    // Indicator row binding
    private void bindIndicatorRow(View row, String indicator, String value, boolean buy) {
        // Row layout has: tvIndicator, tvValue, tvBuyFlag
        ((android.widget.TextView) row.findViewWithTag("indicator")).setText(indicator);
        ((android.widget.TextView) row.findViewWithTag("value")).setText(value);
        android.widget.TextView flag = row.findViewWithTag("flag");
        flag.setText(buy ? "✔ YES" : "✕ NO");
        flag.setTextColor(buy ? Color.parseColor("#27AE60") : Color.parseColor("#E74C3C"));
    }
    // Context strings (mirrors Python badges)
    private String bbContext(double pct) {
        if (pct < 0.2) return String.format(Locale.US, "BB: Near lower band – good buy zone", pct);
        if (pct < 0.4) return String.format(Locale.US, "BB: Below mid – slightly undervalued", pct);
        if (pct < 0.6) return String.format(Locale.US, "BB: Mid band – neutral", pct);
        if (pct < 0.8) return String.format(Locale.US, "BB: Above mid – slightly overvalued", pct);
        return String.format(Locale.US, "BB: Near upper band – sell zone", pct);
    }
    private String rsiContext(double rsi, double mid, int os, int ob) {
        if (rsi < os) return String.format(Locale.US, "RSI: Oversold (%.0f) – bounce possible", rsi);
        if (rsi > ob) return String.format(Locale.US, "RSI: Overbought (%.0f) – reversal possible", rsi);
        if (rsi < mid) return String.format(Locale.US, "RSI: (%.0f) below mid – room to grow", rsi);
        return String.format(Locale.US, "RSI: (%.0f) above mid – weakening", rsi);
    }
    private String macdContext(double hist) {
        return String.format(Locale.US, "%s  hist=%.3f", hist >= 0 ? "MACD: Strengthening ▲" : "MACD: Weakening ▼", hist);
    }
    private static String signalHex(String s) { if ("BUY".equals(s)) return "#2ECC71"; if ("SELL".equals(s)) return "#E74C3C"; return "#F39C12"; }
    private void setupPredictionTabs(Asset asset) {
        if (binding == null) return;
        stylePredTab(true);  // MC selected by default
        binding.btnPredMc.setOnClickListener(v -> {
            if (predMode == PredMode.MONTE_CARLO) return;
            predMode = PredMode.MONTE_CARLO;
            stylePredTab(true);
            runPrediction(asset);
        });
        binding.btnPredProphet.setOnClickListener(v -> {
            if (predMode == PredMode.PROPHET) return;
            predMode = PredMode.PROPHET;
            stylePredTab(false);
            runPrediction(asset);
        });
        styleChart(binding.predChart, new String[0]);
        binding.predChart.setNoDataText("Computing prediction…");
        binding.predChart.setNoDataTextColor(Color.LTGRAY);
    }
    private void stylePredTab(boolean mcSelected) {
        if (binding == null) return;
        int blue = Color.parseColor("#4DA8E8"), dark = Color.parseColor("#1A1D2E");
        binding.btnPredMc.setBackgroundTintList(android.content.res.ColorStateList.valueOf(mcSelected ? blue : dark));
        binding.btnPredMc.setTextColor(mcSelected ? dark : blue);
        binding.btnPredProphet.setBackgroundTintList(android.content.res.ColorStateList.valueOf(mcSelected ? dark : blue));
        binding.btnPredProphet.setTextColor(mcSelected ? blue : dark);
    }
    private void runPrediction(Asset asset) {
        if (asset.ohlcHistory == null || asset.ohlcHistory.isEmpty()) return;
        if (binding != null) binding.tvPredModelLabel.setText("Computing…");
        predExecutor.submit(() -> {
            // Extract price array and timestamps
            int n = asset.ohlcHistory.size();
            double[] prices = new double[n];
            long[] tss = new long[n];
            for (int i = 0; i < n; i++) {
                prices[i] = asset.ohlcHistory.get(i).close;
                tss[i] = asset.ohlcHistory.get(i).timestamp;
            }
            if (predMode == PredMode.MONTE_CARLO) {
                PredictionResult mc = PredictionEngine.predict(prices, asset.profile);
                requireActivity().runOnUiThread(() -> drawMcPrediction(asset, prices, mc));
            } else {
                ProphetResult pr = ProphetLite.forecast(prices, tss);
                requireActivity().runOnUiThread(() -> drawProphetPrediction(asset, prices, pr));
            }
        });
    }
    private void drawMcPrediction(Asset asset, double[] histPrices, PredictionResult mc) {
        if (binding == null || !mc.valid) {
            if (binding != null) {
                binding.tvPredModelLabel.setText("Not enough data for prediction");
                binding.predChart.clear();
                binding.predChart.invalidate();
            }
            return;
        }
        int histN = Math.min(60, histPrices.length);  // show last 60 bars of history
        int totalPoints = histN + PredictionEngine.HORIZON;
        List<Entry> histEntries = new ArrayList<>(histN);
        List<Entry> medianEntries = new ArrayList<>(PredictionEngine.HORIZON);
        List<Entry> p10Entries = new ArrayList<>(PredictionEngine.HORIZON);
        List<Entry> p90Entries = new ArrayList<>(PredictionEngine.HORIZON);
        // History portion
        int hStart = histPrices.length - histN;
        for (int i = 0; i < histN; i++) histEntries.add(new Entry(i, (float) histPrices[hStart + i]));
        // Prediction portion (starts where history ends)
        for (int h = 0; h < PredictionEngine.HORIZON; h++) {
            float x = histN + h;
            medianEntries.add(new Entry(x, (float) mc.median[h]));
            p10Entries.add(new Entry(x, (float) mc.p10[h]));
            p90Entries.add(new Entry(x, (float) mc.p90[h]));
        }
        // Fan chart: draw a subset of paths as thin transparent lines
        List<ILineDataSet> dataSets = new ArrayList<>();
        int mcColor = Color.parseColor(mc.color);
        for (int p = 0; p < Math.min(40, PredictionEngine.N_PATHS); p++) {
            List<Entry> fanEntries = new ArrayList<>(PredictionEngine.HORIZON);
            // Start fan from last historical price
            fanEntries.add(new Entry(histN - 1, (float) histPrices[histPrices.length - 1]));
            for (int h = 0; h < PredictionEngine.HORIZON; h++)
                fanEntries.add(new Entry(histN + h, (float) mc.paths[p][h]));
            LineDataSet fan = new LineDataSet(fanEntries, "");
            fan.setColor(adjustAlpha(mcColor, 40));
            fan.setLineWidth(0.6f);
            fan.setDrawCircles(false);
            fan.setDrawValues(false);
            fan.setHighlightEnabled(false);
            fan.setLabel("");
            dataSets.add(fan);
        }
        // P10/P90 filled band
        LineDataSet p90ds = new LineDataSet(p90Entries, "P90");
        p90ds.setColor(adjustAlpha(mcColor, 80));
        p90ds.setLineWidth(1f);
        p90ds.setDrawCircles(false);
        p90ds.setDrawValues(false);
        p90ds.setFillColor(mcColor);
        p90ds.setFillAlpha(35);
        p90ds.setDrawFilled(true);
        LineDataSet p10ds = new LineDataSet(p10Entries, "P10");
        p10ds.setColor(adjustAlpha(mcColor, 80));
        p10ds.setLineWidth(1f);
        p10ds.setDrawCircles(false);
        p10ds.setDrawValues(false);
        // History
        LineDataSet histDs = lineDataSet(histEntries, "History", "#B0BEC5", 1.5f);
        // Median
        LineDataSet medDs = new LineDataSet(medianEntries, "Median");
        medDs.setColor(mcColor);
        medDs.setLineWidth(2.5f);
        medDs.setDrawCircles(false);
        medDs.setDrawValues(false);
        medDs.setMode(LineDataSet.Mode.LINEAR);
        dataSets.add(p90ds);
        dataSets.add(p10ds);
        dataSets.add(histDs);
        dataSets.add(medDs);
        // X labels: last 60 history + 30 future
        String[] xl = buildXLabels(asset, histN, PredictionEngine.HORIZON);
        LineData ld = new LineData(dataSets);
        binding.predChart.setData(ld);
        styleChart(binding.predChart, xl);
        binding.predChart.invalidate();
        binding.tvPredModelLabel.setText(mc.label + "  (" + mc.shortLabel + ")  ·  200 paths");
        binding.tvPredBandLabel.setText("P10 / P90 band");
        binding.legendColorDot.setBackgroundColor(mcColor);
    }
    private void drawProphetPrediction(Asset asset, double[] histPrices, ProphetResult pr) {
        if (binding == null || !pr.valid) {
            if (binding != null) {
                binding.tvPredModelLabel.setText("Not enough data for Prophet");
                binding.predChart.clear(); binding.predChart.invalidate();
            }
            return;
        }
        int histN = Math.min(60, histPrices.length);
        List<Entry> histEntries = new ArrayList<>(histN);
        List<Entry> forecastEntries = new ArrayList<>(ProphetLite.HORIZON);
        List<Entry> upperEntries = new ArrayList<>(ProphetLite.HORIZON);
        List<Entry> lowerEntries = new ArrayList<>(ProphetLite.HORIZON);
        int hStart = histPrices.length - histN;
        for (int i = 0; i < histN; i++)
            histEntries.add(new Entry(i, (float) histPrices[hStart + i]));
        for (int h = 0; h < ProphetLite.HORIZON; h++) {
            float x = histN + h;
            forecastEntries.add(new Entry(x, (float) pr.forecast[h]));
            upperEntries.add(new Entry(x, (float) pr.upper[h]));
            lowerEntries.add(new Entry(x, (float) pr.lower[h]));
        }
        int prophetColor = Color.parseColor(ProphetResult.COLOR);
        LineDataSet histDs = lineDataSet(histEntries, "History", "#B0BEC5", 1.5f);
        LineDataSet upperDs = new LineDataSet(upperEntries, "Upper");
        upperDs.setColor(adjustAlpha(prophetColor, 90));
        upperDs.setLineWidth(1f);
        upperDs.setDrawCircles(false);
        upperDs.setDrawValues(false);
        upperDs.setFillColor(prophetColor);
        upperDs.setFillAlpha(40);
        upperDs.setDrawFilled(true);
        LineDataSet lowerDs = new LineDataSet(lowerEntries, "Lower");
        lowerDs.setColor(adjustAlpha(prophetColor, 90));
        lowerDs.setLineWidth(1f);
        lowerDs.setDrawCircles(false);
        lowerDs.setDrawValues(false);
        LineDataSet fcDs = new LineDataSet(forecastEntries, "Forecast");
        fcDs.setColor(prophetColor);
        fcDs.setLineWidth(2.5f);
        fcDs.setDrawCircles(false);
        fcDs.setDrawValues(false);
        fcDs.setMode(LineDataSet.Mode.LINEAR);
        // Connect last history point to first forecast point
        List<Entry> connector = new ArrayList<>();
        connector.add(new Entry(histN - 1, (float) histPrices[histPrices.length - 1]));
        connector.add(new Entry(histN, (float) pr.forecast[0]));
        LineDataSet connDs = lineDataSet(connector, "", "#B0BEC5", 1.5f);
        String[] xl = buildXLabels(asset, histN, ProphetLite.HORIZON);
        LineData ld = new LineData(histDs, connDs, upperDs, lowerDs, fcDs);
        binding.predChart.setData(ld);
        styleChart(binding.predChart, xl);
        binding.predChart.invalidate();
        binding.tvPredModelLabel.setText(ProphetResult.LABEL);
        binding.tvPredBandLabel.setText("± 1.5 MAE band");
        binding.legendColorDot.setBackgroundColor(prophetColor);
    }
    private String[] buildXLabels(Asset asset, int histN, int futureN) {
        String[] xl = new String[histN + futureN];
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
        int hStart = asset.ohlcHistory.size() - histN;
        for (int i = 0; i < histN; i++) xl[i] = sdf.format(new Date(asset.ohlcHistory.get(hStart + i).timestamp));
        // Future labels: increment by estimated bar duration
        long lastTs = asset.ohlcHistory.get(asset.ohlcHistory.size() - 1).timestamp;
        long barMs = estimateBarMs(interval);
        for (int h = 0; h < futureN; h++) xl[histN + h] = sdf.format(new Date(lastTs + (h + 1) * barMs));
        return xl;
    }
    private long estimateBarMs(String interval) {
        if (interval == null) return 86_400_000L;
        switch (interval) {
            case "1m": return 60_000L;
            case "5m": return 300_000L;
            case "15m": return 900_000L;
            case "30m": return 1_800_000L;
            case "1h": return 3_600_000L;
            case "4h": return 14_400_000L;
            default: return 86_400_000L;  // 1d
        }
    }
    private static int adjustAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | (alpha << 24); }
    @Override
    public void onDestroyView() { super.onDestroyView(); predExecutor.shutdownNow(); binding = null; }
}