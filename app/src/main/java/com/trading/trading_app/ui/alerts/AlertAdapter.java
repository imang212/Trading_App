package com.trading.trading_app.ui.alerts;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.trading.app.databinding.ItemAlertRuleBinding;
import com.trading.trading_app.model.AlertRule;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlertAdapter extends ListAdapter<AlertRule, AlertAdapter.ViewHolder> {

    public interface OnToggle { void onToggle(AlertRule rule, boolean active); }
    public interface OnDelete { void onDelete(AlertRule rule); }

    private final OnToggle onToggle;
    private final OnDelete onDelete;

    public AlertAdapter(OnToggle onToggle, OnDelete onDelete) {
        super(DIFF);
        this.onToggle = onToggle;
        this.onDelete = onDelete;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAlertRuleBinding b = ItemAlertRuleBinding.inflate(
            LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), onToggle, onDelete);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemAlertRuleBinding b;
        ViewHolder(ItemAlertRuleBinding b) { super(b.getRoot()); this.b = b; }

        void bind(AlertRule rule, OnToggle onToggle, OnDelete onDelete) {
            b.tvAlertType.setText(rule.typeLabel());
            int typeColor;
            switch (rule.type) {
                case BUY_OPPORTUNITY: typeColor = Color.parseColor("#2ECC71"); break;
                case SELL_REVERSAL:   typeColor = Color.parseColor("#E74C3C"); break;
                default:              typeColor = Color.parseColor("#4DA8E8"); break;
            }
            b.tvAlertType.setTextColor(typeColor);
            b.tvAlertAsset.setText(rule.assetName + (rule.ticker != null ? "  (" + rule.ticker + ")" : ""));

            switch (rule.type) {
                case PRICE_ABOVE:
                    b.tvAlertThreshold.setText(String.format(Locale.US, "Trigger when price ≥ %.2f", rule.threshold));
                    b.tvAlertThreshold.setVisibility(android.view.View.VISIBLE);
                    break;
                case PRICE_BELOW:
                    b.tvAlertThreshold.setText(String.format(Locale.US, "Trigger when price ≤ %.2f", rule.threshold));
                    b.tvAlertThreshold.setVisibility(android.view.View.VISIBLE);
                    break;
                case BUY_OPPORTUNITY:
                    b.tvAlertThreshold.setText("High volume + Bayes score + RSI<48 + bullish MACD/EMA");
                    b.tvAlertThreshold.setVisibility(android.view.View.VISIBLE);
                    break;
                case SELL_REVERSAL:
                    b.tvAlertThreshold.setText("RSI>58 + bearish MACD + price<EMA + volume confirm");
                    b.tvAlertThreshold.setVisibility(android.view.View.VISIBLE);
                    break;
            }

            String lastFired = rule.lastTriggeredAt > 0
                ? new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(new Date(rule.lastTriggeredAt))
                : "never";
            b.tvAlertStats.setText(String.format(Locale.US,
                "Triggered %d×  ·  Last: %s", rule.triggerCount, lastFired));

            b.switchAlertActive.setOnCheckedChangeListener(null);
            b.switchAlertActive.setChecked(rule.active);
            b.switchAlertActive.setOnCheckedChangeListener((btn, checked) -> onToggle.onToggle(rule, checked));

            b.btnDeleteAlert.setOnClickListener(v -> onDelete.onDelete(rule));
        }
    }

    private static final DiffUtil.ItemCallback<AlertRule> DIFF = new DiffUtil.ItemCallback<AlertRule>() {
        @Override public boolean areItemsTheSame(@NonNull AlertRule a, @NonNull AlertRule b) {
            return a.id.equals(b.id);
        }
        @Override public boolean areContentsTheSame(@NonNull AlertRule a, @NonNull AlertRule b) {
            return a.active == b.active && a.triggerCount == b.triggerCount
                && a.lastTriggeredAt == b.lastTriggeredAt;
        }
    };
}
