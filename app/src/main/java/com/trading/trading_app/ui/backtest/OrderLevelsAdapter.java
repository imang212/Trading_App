package com.trading.trading_app.ui.backtest;

import android.graphics.Color;
import android.view.LayoutInflater; import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil; import androidx.recyclerview.widget.ListAdapter; import androidx.recyclerview.widget.RecyclerView;
import com.trading.app.databinding.ItemOrderRowBinding;
import com.trading.trading_app.model.Asset;
/** Adapter for the Order Levels screen. Each row: Asset | Price | Signal | Buy Limit | Stop-Loss | TP1 | TP2 | Risk USD*/
public class OrderLevelsAdapter extends ListAdapter<Asset, OrderLevelsAdapter.VH> {
    public OrderLevelsAdapter() { super(DIFF); }
    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemOrderRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) { holder.bind(getItem(position)); }
    static class VH extends RecyclerView.ViewHolder {
        final ItemOrderRowBinding b;
        VH(ItemOrderRowBinding b) { super(b.getRoot()); this.b = b; }
        void bind(Asset a) {
            double price = a.price, bl = a.buyLimit, sl = a.stopLoss, tp1 = a.takeProfit1, tp2 = a.sellTarget;
            double riskPer = bl - sl;
            b.tvOrdAsset.setText(a.name);
            b.tvOrdProfile.setText(a.profile);
            b.tvOrdPrice.setText(String.format("$%,.2f", price));
            b.tvOrdSignal.setText(a.signal);
            b.tvOrdSignal.setTextColor(signalColor(a.signal));
            b.tvOrdBuyLimit.setText(String.format("$%,.2f\n%+.1f%%", bl, pct(bl, price)));
            b.tvOrdStopLoss.setText(String.format("$%,.2f\n%+.1f%%", sl, pct(sl, price)));
            b.tvOrdTp1.setText(String.format("$%,.2f\n1:%.1f", tp1, rr(tp1, bl, riskPer)));
            b.tvOrdTp2.setText(String.format("$%,.2f\n1:%.1f", tp2, rr(tp2, bl, riskPer)));
            b.tvOrdRisk.setText(String.format("$%,.0f", a.riskUsd));
            // Zebra striping
            int bg = getAdapterPosition() % 2 == 0 ? Color.parseColor("#1A1D2E") : Color.parseColor("#1E2130");
            b.getRoot().setBackgroundColor(bg);
        }
        private double pct(double level, double price) { return price > 0 ? (level - price) / price * 100 : 0; }
        private double rr(double target, double entry, double risk) { return risk > 0 ? Math.abs((target - entry) / risk) : 0; }
        private int signalColor(String s) {
            if ("BUY".equals(s))  return Color.parseColor("#2ECC71");
            if ("SELL".equals(s)) return Color.parseColor("#E74C3C");
            return Color.parseColor("#F39C12");
        }
    }
    private static final DiffUtil.ItemCallback<Asset> DIFF =
        new DiffUtil.ItemCallback<Asset>() {
            @Override public boolean areItemsTheSame(@NonNull Asset a, @NonNull Asset b) { return a.name.equals(b.name); }
            @Override public boolean areContentsTheSame(@NonNull Asset a, @NonNull Asset b) {
                return Double.compare(a.buyLimit, b.buyLimit) == 0 && Double.compare(a.stopLoss, b.stopLoss) == 0;
            }
        };
}