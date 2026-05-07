package com.trading.trading_app.ui.detail;

import android.graphics.Color; import android.os.Bundle;
import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup;
import androidx.annotation.NonNull; import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.trading.app.databinding.FragmentAssetDetailBinding;
import com.trading.trading_app.model.Asset; import com.trading.trading_app.model.Asset.OhlcBar;
import com.trading.trading_app.repository.TradingRepository; import com.trading.trading_app.viewmodel.SignalViewModel;
import java.text.SimpleDateFormat;
import java.util.ArrayList; import java.util.Date; import java.util.List; import java.util.Locale;
/**Asset detail screen. Charts (all via MPAndroidChart):
 *   1. Candlestick chart + EMA lines + BB bands + Buy/Sell markers + Volume bars
 *   2. RSI line chart
 *   3. MACD bar + signal line chart
 *   4. ATR area chart
 * Price levels, indicator status table, speed & volume — same as Streamlit dashboard.*/
public class AssetDetailFragment extends Fragment {
    public static final String ARG_ASSET_NAME = "asset_name", ARG_INTERVAL   = "interval";
    private FragmentAssetDetailBinding binding; private SignalViewModel vm;
    private String assetName, interval;
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAssetDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
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
                @Override public void onSuccess(Asset asset) { requireActivity().runOnUiThread(() -> populateUi(asset)); }
                @Override public void onError(String msg) {
                    requireActivity().runOnUiThread(() -> { binding.progressDetail.setVisibility(View.GONE); binding.tvDetailError.setVisibility(View.VISIBLE); binding.tvDetailError.setText("Error: " + msg);});
                }
            });
    }
    // Populate UI
    private void populateUi(Asset asset) {
        binding.progressDetail.setVisibility(View.GONE); binding.scrollContent.setVisibility(View.VISIBLE);
        // Header
        binding.tvDetailPrice.setText(String.format("%s%,.2f", asset.currencySymbol, asset.price));
        String chg = String.format("%+.2f%%", asset.priceChange);
        binding.tvDetailChange.setText(chg);
        binding.tvDetailChange.setTextColor(asset.priceChange >= 0 ? Color.parseColor("#2ECC71") : Color.parseColor("#E74C3C"));
        binding.tvDetailSignal.setText(asset.signal);
        binding.tvDetailSignal.setTextColor(Color.parseColor(signalHex(asset.signal)));
        binding.tvDetailScore.setText(String.format(Locale.US, "Score %d/5  |  BUY %d%%  SELL %d%%", asset.buyScore, asset.bayesBuyScore, asset.bayesSellScore));
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
        bindIndicatorRow(binding.rowMA.getRoot(), "MA Crossover", String.format("EMA%d %s EMA%d", asset.maShort, asset.condMA?">":"<", asset.maLong), asset.condMA);
        bindIndicatorRow(binding.rowRsi.getRoot(), "RSI", String.format("%.1f", asset.rsi), asset.condRSI);
        bindIndicatorRow(binding.rowBb.getRoot(), "Bollinger", String.format("BB%% %.2f", asset.bbPct), asset.condBB);
        bindIndicatorRow(binding.rowMacd.getRoot(), "MACD", String.format("hist %.3f", asset.macdHist), asset.condMACD);
        bindIndicatorRow(binding.rowAtr.getRoot(), "ATR trend", String.format("Price %s SMA%d", asset.price > asset.smaShort ? ">" : "<", asset.maShort), asset.condATR);
        // Speed & Volume
        binding.tvRoc.setText(String.format("%+.2f%%", asset.roc));
        binding.tvAtrChg.setText(String.format("%+.1f%%", asset.atrChg));
        binding.tvBodyRatio.setText(String.format("%.1fx avg", asset.bodyRatio));
        binding.tvVolRatioDetail.setText(asset.volRatio > 0 ? String.format("%+.0f%% vs avg", (asset.volRatio-1)*100) : "N/A");
        binding.tvObvSignal.setText(asset.obvSignal);
        // Charts
        if (asset.ohlcHistory != null && !asset.ohlcHistory.isEmpty()) { setupCandleChart(asset); setupRsiChart(asset); setupMacdChart(asset); setupAtrChart(asset); }
    }
    // Candlestick + Volume + EMA + BB
    private void setupCandleChart(Asset asset) {
        List<OhlcBar> bars = asset.ohlcHistory; int n = bars.size();
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
            emaShort.add(new Entry(i, b.emaShort)); emaLong.add(new Entry(i, b.emaLong));
            bbUpper.add(new Entry(i, b.bbUpper)); bbLower.add(new Entry(i, b.bbLower));
            volume.add(new BarEntry(i, b.volume));
            xLabels[i] = sdf.format(new Date(b.timestamp));
            if (b.signalFlag ==  1) buyMarkers.add(new Entry(i, b.low  * 0.995f));
            if (b.signalFlag == -1) selMarkers.add(new Entry(i, b.high * 1.005f));
        }
        // Candles dataset
        CandleDataSet cds = new CandleDataSet(candles, "Price");
        cds.setDecreasingColor(Color.parseColor("#E74C3C")); cds.setIncreasingColor(Color.parseColor("#2ECC71"));
        cds.setDecreasingPaintStyle(android.graphics.Paint.Style.FILL); cds.setIncreasingPaintStyle(android.graphics.Paint.Style.FILL);
        cds.setShadowColor(Color.GRAY); cds.setShadowWidth(0.7f); cds.setDrawValues(false);
        // EMA lines
        LineDataSet emsDS = lineDataSet(emaShort, "EMA" + asset.maShort, "#F39C12", 1.5f);
        LineDataSet emlDS = lineDataSet(emaLong, "EMA" + asset.maLong,  "#3498DB", 1.5f);
        LineDataSet bbUDS = lineDataSet(bbUpper, "BB Upper", "#4FC3F7", 1f);
        LineDataSet bbLDS = lineDataSet(bbLower, "BB Lower", "#4FC3F7", 1f);
        bbLDS.setFillAlpha(30); bbLDS.setFillColor(Color.parseColor("#4FC3F7")); bbLDS.setDrawFilled(true);
        // Buy/Sell markers
        LineDataSet buyDS = markerSet(buyMarkers, "BUY",  "#2ECC71", true);
        LineDataSet selDS = markerSet(selMarkers, "SELL", "#E74C3C", false);
        binding.candleChart.setData(new CombinedData());
        CombinedData combined = new CombinedData();
        combined.setData(new CandleData(cds));
        combined.setData(new LineData(emsDS, emlDS, bbUDS, bbLDS, buyDS, selDS));
        binding.candleChart.setData(combined);
        styleChart(binding.candleChart, xLabels);
        // Horizontal price level lines
        addLimitLine(binding.candleChart.getAxisLeft(), asset.buyLimit,  "#2ECC71", "Buy Limit");
        addLimitLine(binding.candleChart.getAxisLeft(), asset.stopLoss,  "#E74C3C", "Stop");
        addLimitLine(binding.candleChart.getAxisLeft(), asset.takeProfit1,"#3498DB","TP1");
        binding.candleChart.invalidate();
    }
    // RSI chart
    private void setupRsiChart(Asset asset) {
        List<OhlcBar> bars = asset.ohlcHistory;
        int n = bars.size(); List<Entry> rsiEntries = new ArrayList<>(n); String[] xl = new String[n];
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
        binding.rsiChart.getAxisLeft().setAxisMinimum(0); binding.rsiChart.getAxisLeft().setAxisMaximum(100);
        binding.rsiChart.invalidate();
    }
    // MACD chart
    private void setupMacdChart(Asset asset) {
        List<OhlcBar> bars = asset.ohlcHistory;
        int n = bars.size(); List<BarEntry> hist = new ArrayList<>(n); List<Entry> macd = new ArrayList<>(n), sig = new ArrayList<>(n); String[] xl = new String[n];
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
        int[] histColors = new int[n];
        for (int i = 0; i < n; i++) {
            OhlcBar b = bars.get(i);
            hist.add(new BarEntry(i, b.macdHist)); macd.add(new Entry(i, b.macd)); sig.add(new Entry(i, b.macdSig));
            xl[i] = sdf.format(new Date(b.timestamp));
            histColors[i] = b.macdHist >= 0 ? Color.parseColor("#2ECC71") : Color.parseColor("#E74C3C");
        }
        BarDataSet histDs = new BarDataSet(hist, "Histogram");
        histDs.setColors(histColors); histDs.setDrawValues(false);
        LineDataSet macdDs = lineDataSet(macd, "MACD", "#378ADD", 1.5f);
        LineDataSet sigDs = lineDataSet(sig, "Signal", "#E74C3C", 1.5f);
        CombinedData cd = new CombinedData();
        cd.setData(new BarData(histDs)); cd.setData(new LineData(macdDs, sigDs));
        binding.macdChart.setData(cd); styleChart(binding.macdChart, xl); binding.macdChart.invalidate();
    }
    // ATR chart
    private void setupAtrChart(Asset asset) {
        List<OhlcBar> bars = asset.ohlcHistory;
        int n = bars.size(); List<Entry> atrEntries = new ArrayList<>(n); String[] xl = new String[n];
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
        for (int i = 0; i < n; i++) {
            atrEntries.add(new Entry(i, bars.get(i).atr));
            xl[i] = sdf.format(new Date(bars.get(i).timestamp));
        }
        LineDataSet ds = lineDataSet(atrEntries, "ATR", "#9B59B6", 1.5f);
        ds.setFillAlpha(40); ds.setFillColor(Color.parseColor("#9B59B6")); ds.setDrawFilled(true);
        binding.atrChart.setData(new LineData(ds)); styleChart(binding.atrChart, xl); binding.atrChart.invalidate();
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
        ds.setColor(Color.parseColor(hex)); ds.setLineWidth(width); ds.setDrawCircles(false); ds.setDrawValues(false); ds.setMode(LineDataSet.Mode.LINEAR);
        return ds;
    }
    private LineDataSet markerSet(List<Entry> entries, String label, String hex, boolean up) {
        LineDataSet ds = new LineDataSet(entries, label);
        ds.setColor(Color.parseColor(hex)); ds.setDrawCircles(true); ds.setCircleColor(Color.parseColor(hex)); ds.setCircleRadius(5f); ds.setDrawValues(false); ds.setLineWidth(0);
        return ds;
    }
    private void addLimitLine(YAxis axis, double value, String hex, String label) {
        LimitLine ll = new LimitLine((float) value, label);
        ll.setLineColor(Color.parseColor(hex)); ll.setLineWidth(1f);
        ll.setTextColor(Color.parseColor(hex)); ll.setTextSize(9f);
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
        if (pct < 0.2) return "🟢 Near lower band – good buy zone";
        if (pct < 0.4) return "🟡 Below mid – slightly undervalued";
        if (pct < 0.6) return "⚪ Mid band – neutral";
        if (pct < 0.8) return "🟠 Above mid – slightly overvalued";
        return "🔴 Near upper band – sell zone";
    }
    private String rsiContext(double rsi, double mid, int os, int ob) {
        if (rsi < os) return String.format(Locale.US, "🟢 Oversold (%.0f) – bounce possible", rsi);
        if (rsi > ob) return String.format(Locale.US, "🔴 Overbought (%.0f) – reversal possible", rsi);
        if (rsi < mid) return String.format(Locale.US, "🟡 (%.0f) below mid – room to grow", rsi);
        return String.format(Locale.US, "🟠 (%.0f) above mid – weakening", rsi);
    }
    private String macdContext(double hist) { return String.format(Locale.US, "%s  hist=%.3f", hist >= 0 ? "🟢 Strengthening ▲" : "🔴 Weakening ▼", hist); }
    private static String signalHex(String s) { if ("BUY".equals(s))  return "#2ECC71"; if ("SELL".equals(s)) return "#E74C3C"; return "#F39C12"; }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}