package com.trading.trading_app.ui.overview;

import android.graphics.Color;
import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup;
import androidx.annotation.NonNull; import androidx.recyclerview.widget.DiffUtil; import androidx.recyclerview.widget.ListAdapter; import androidx.recyclerview.widget.RecyclerView;
import com.trading.app.databinding.ItemSignalCardBinding;
import com.trading.trading_app.model.Asset;
import java.util.ArrayList; import java.util.Collections; import java.util.List; import java.util.Locale;
/**RecyclerView adapter for signal cards. Uses ListAdapter + DiffUtil for efficient O(n) diff updates — same as how the Streamlit table updates when data changes.*/
public class SignalAdapter extends ListAdapter<Asset, SignalAdapter.ViewHolder> {
    public interface OnAssetClickListener { void onClick(Asset asset); }
    public enum SortOrder { DEFAULT, BAYES_DESC, BAYES_ASC }
    private final OnAssetClickListener listener;
    private List<Asset> originalList = new ArrayList<>();
    public SignalAdapter(OnAssetClickListener listener) { super(DIFF_CALLBACK); this.listener = listener; }
    /** Called from Fragment — keeps original list for re-sorting */
    /** Unified 0–10 score used for sorting. 10 = strongest BUY, 0 = strongest SELL, 5 = neutral. All assets are comparable on the same scale regardless of signal type.*/
    private double bestScore(Asset a) { return a.bayesBuyScore; }
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
            String bayesText = String.format(java.util.Locale.US,"B:%.2f/%.2f", asset.bayesBuyScore, asset.bayesThresholdBuy);
            b.tvBayesScore.setText("(" + bayesText + ")");
            b.tvBayesScore.setTextColor(asset.bayesBuyScore >= asset.bayesThresholdBuy ? android.graphics.Color.parseColor("#2ECC71") : android.graphics.Color.parseColor("#9BA8BE"));
            b.tvBayesScore.setAlpha(0.85f);
            // setupScoreBar(asset.buyScore, asset.signal); // Score bar (5 segments)
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
        private static String signalHex(String s) { if ("BUY".equals(s))  return "#2ECC71"; if ("SELL".equals(s)) return "#E74C3C"; return "#F39C12"; }
    }
    // DiffUtil
    private static final DiffUtil.ItemCallback<Asset> DIFF_CALLBACK = new DiffUtil.ItemCallback<Asset>() {
            @Override public boolean areItemsTheSame(@NonNull Asset a, @NonNull Asset b) { return a.name.equals(b.name); }
            @Override public boolean areContentsTheSame(@NonNull Asset a, @NonNull Asset b) {
                return a.signal.equals(b.signal) && Double.compare(a.price, b.price) == 0 && a.buyScore == b.buyScore && Double.compare(a.bayesBuyScore, b.bayesBuyScore) == 0;
            }
        };

}