package com.trading.trading_app.ui.overview;

import android.graphics.Color;
import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil; import androidx.recyclerview.widget.ListAdapter; import androidx.recyclerview.widget.RecyclerView;
import com.trading.app.databinding.ItemSignalCardBinding;
import com.trading.trading_app.model.Asset;
import java.util.ArrayList; import java.util.Collections; import java.util.List; import java.util.Locale;
/**RecyclerView adapter for signal cards. Uses ListAdapter + DiffUtil for efficient O(n) diff updates — same as how the Streamlit table updates when data changes.*/
public class SignalAdapter extends ListAdapter<Asset, SignalAdapter.ViewHolder> {
    public interface OnAssetClickListener { void onClick(Asset asset); }
    public enum SortOrder { DEFAULT, BAYES_DESC, BAYES_ASC }
    private final OnAssetClickListener listener;
    private SortOrder sortOrder = SortOrder.DEFAULT;
    private List<Asset> originalList = new ArrayList<>();
    public SignalAdapter(OnAssetClickListener listener) { super(DIFF_CALLBACK); this.listener = listener; }
    /** Called from Fragment — keeps original list for re-sorting */
    public void submitSortedList(List<Asset> list) {
        originalList = list != null ? new ArrayList<>(list) : new ArrayList<>();
        applySort();
    }
    public void setSortOrder(SortOrder order) { this.sortOrder = order; applySort(); }
    public SortOrder getSortOrder() { return sortOrder; }
    private void applySort() {
        List<Asset> sorted = new ArrayList<>(originalList);
        if (sortOrder == SortOrder.BAYES_DESC) { Collections.sort(sorted, (a, b) -> Double.compare(bestScore(b), bestScore(a))); } // Best Bayes score first — for BUY signals use buyProb, SELL use sellProb
        else if (sortOrder == SortOrder.BAYES_ASC) { Collections.sort(sorted, (a, b) -> Double.compare(bestScore(a), bestScore(b))); }
        submitList(sorted);
    }
    /** Returns the relevant Bayes probability for sorting: BUY assets → buyProb, SELL → sellProb, NEU → 0.5 */
    private double bestScore(Asset a) { if ("BUY".equals(a.signal)) return a.bayesBuyProb; if ("SELL".equals(a.signal)) return a.bayesSellProb; return 0.5; }
    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSignalCardBinding b = ItemSignalCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(b);
    }
    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) { holder.bind(getItem(position), listener); }
    // ViewHolder
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemSignalCardBinding b;
        ViewHolder(ItemSignalCardBinding b) { super(b.getRoot()); this.b = b; }
        void bind(Asset asset, OnAssetClickListener listener) {
            // Name & profile
            b.tvAssetName.setText(asset.name); b.tvProfile.setText(asset.profile); b.tvTicker.setText(asset.ticker);
            // Price
            b.tvPrice.setText(String.format("%s%,.2f", asset.currencySymbol, asset.price));
            String changeStr = String.format("%+.2f%%", asset.priceChange);
            b.tvChange.setText(changeStr); b.tvChange.setTextColor(asset.priceChange >= 0 ? Color.parseColor("#2ECC71") : Color.parseColor("#E74C3C"));
            // Signal badge
            b.tvSignal.setText(asset.signal); b.tvSignal.setTextColor(Color.parseColor(signalHex(asset.signal)));
            b.viewSignalBar.setBackgroundColor(asset.signalColor());
            // Bayes score next to signal — show the relevant probability in ()
            String bayesText = asset.bayesScoreBadge();
            b.tvBayesScore.setText("(" + bayesText + ")"); b.tvBayesScore.setTextColor(Color.parseColor(signalHex(asset.signal))); b.tvBayesScore.setAlpha(0.75f);
            // Score bar (5 segments)
            setupScoreBar(asset.buyScore, asset.signal);
            // Key indicators row
            b.tvRsi.setText(String.format("RSI %.0f", asset.rsi));
            b.tvBbPct.setText(String.format("BB%% %.2f", asset.bbPct));
            b.tvMacdHist.setText(String.format("MACD %.3f", asset.macdHist));
            b.tvVolRatio.setText(asset.volRatio > 0 ? String.format("Vol %+.0f%%", (asset.volRatio - 1) * 100) : "Vol N/A");
            // Condition ticks
            int buyColor = Color.parseColor("#2ECC71"); int sellColor = Color.parseColor("#E74C3C");
            bindCond(b.ivCondMa, asset.condMA, "MA"); bindCond(b.ivCondRsi, asset.condRSI, "RSI"); bindCond(b.ivCondBb, asset.condBB, "BB");
            bindCond(b.ivCondMacd, asset.condMACD, "MACD"); bindCond(b.ivCondAtr, asset.condATR, "ATR");
            b.ivCondMa.setTextColor(asset.condMA ? buyColor : sellColor); b.ivCondRsi.setTextColor(asset.condRSI ? buyColor : sellColor); b.ivCondBb.setTextColor(asset.condBB ? buyColor : sellColor);
            b.ivCondMacd.setTextColor(asset.condMACD ? buyColor : sellColor); b.ivCondAtr.setTextColor(asset.condATR ? buyColor : sellColor);
            // Order levels (compact)
            b.tvBuyLimit.setText(String.format("BL %s%,.2f", asset.currencySymbol, asset.buyLimit));
            b.tvStopLoss.setText(String.format("SL %s%,.2f", asset.currencySymbol, asset.stopLoss));
            b.tvTakeProfit.setText(String.format("TP %s%,.2f", asset.currencySymbol, asset.takeProfit1));
            b.getRoot().setOnClickListener(v -> listener.onClick(asset));
        }
        private void bindCond(android.widget.TextView tv, boolean cond, String label) { tv.setText(cond ? "✔ " + label : "✕ " + label); }
        private void setupScoreBar(int score, String signal) {
            View[] segs = {b.seg0, b.seg1, b.seg2, b.seg3, b.seg4};
            int fillColor;
            if ("SELL".equals(signal)) {
                fillColor = score <= 1 ? Color.parseColor("#E74C3C") : score == 2 ? Color.parseColor("#F39C12") : Color.parseColor("#2ECC71");
            }
            else {
                fillColor = score >= 4 ? Color.parseColor("#2ECC71") : score == 3 ? Color.parseColor("#F39C12") : Color.parseColor("#E74C3C");
            }
            for (int i = 0; i < segs.length; i++) segs[i].setBackgroundColor(i < score ? fillColor : Color.parseColor("#3A3A3A"));
        }
        private static String signalHex(String s) { if ("BUY".equals(s))  return "#2ECC71"; if ("SELL".equals(s)) return "#E74C3C"; return "#F39C12"; }
    }
    // DiffUtil
    private static final DiffUtil.ItemCallback<Asset> DIFF_CALLBACK = new DiffUtil.ItemCallback<Asset>() {
            @Override public boolean areItemsTheSame(@NonNull Asset a, @NonNull Asset b) { return a.name.equals(b.name); }
            @Override public boolean areContentsTheSame(@NonNull Asset a, @NonNull Asset b) {
                return a.signal.equals(b.signal)
                        && Double.compare(a.price, b.price) == 0
                        && a.buyScore == b.buyScore
                        && a.bayesBuyScore == b.bayesBuyScore;
            }
        };

}