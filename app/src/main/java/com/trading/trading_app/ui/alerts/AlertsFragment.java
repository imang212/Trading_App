package com.trading.trading_app.ui.alerts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull; import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.trading.app.R;
import com.trading.app.databinding.FragmentAlertsBinding;
import com.trading.trading_app.model.AlertRule;
import com.trading.trading_app.model.AlertStore;
import com.trading.trading_app.model.AppConfig;
import com.trading.trading_app.viewmodel.SignalViewModel;

import java.util.ArrayList; import java.util.List;

public class AlertsFragment extends Fragment {
    private FragmentAlertsBinding binding;
    private AlertAdapter alertAdapter;
    private AlertStore store;
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAlertsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        store = AlertStore.getInstance(requireContext());
        setupRecyclerView();
        binding.btnAddAlert.setOnClickListener(v -> showAddAlertDialog());
        refreshList();
    }
    private void setupRecyclerView() {
        alertAdapter = new AlertAdapter(
            // onToggle
            (rule, active) -> {
                rule.active = active;
                store.update(rule);
            },
            // onDelete
            rule -> {
                store.delete(rule.id);
                refreshList();
                Toast.makeText(getContext(), "Alert deleted", Toast.LENGTH_SHORT).show();
            }
        );
        binding.recyclerAlerts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerAlerts.setAdapter(alertAdapter);
    }
    private void refreshList() {
        List<AlertRule> rules = store.loadAll();
        alertAdapter.submitList(new ArrayList<>(rules));
        binding.tvAlertsEmpty.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);
    }
    private void showAddAlertDialog() {
        // Build asset names list from AppConfig
        List<String> assetNames = new ArrayList<>(AppConfig.ASSETS.keySet());
        java.util.Collections.sort(assetNames);
        String[] assetArr = assetNames.toArray(new String[0]);
        // Type options
        String[] types = {
                "BUY Opportunity (volume + indicators)",
                "SELL Reversal (RSI + MACD + EMA)",
                "⬆ Price Above threshold",
                "⬇ Price Below threshold"
        };
        // Step 1: pick asset — custom adapter so list text uses our dark theme colors
        ArrayAdapter<String> assetAdapter = new ArrayAdapter<>(
                requireContext(), R.layout.dialog_list_item, assetArr);
        new MaterialAlertDialogBuilder(requireContext(), R.style.Widget_TradingApp_Dialog)
                .setTitle("Select Asset")
                .setAdapter(assetAdapter, (d, assetIdx) -> {
                    String assetName = assetArr[assetIdx];
                    String ticker = AppConfig.ASSETS.getOrDefault(assetName, assetName);
                    String profile = AppConfig.ASSET_PROFILES.getOrDefault(assetName, "TECH");
                    // Step 2: pick alert type — same custom adapter approach
                    ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                            requireContext(), R.layout.dialog_list_item, types);
                    new MaterialAlertDialogBuilder(requireContext(), R.style.Widget_TradingApp_Dialog)
                            .setTitle("Alert type for " + assetName)
                            .setAdapter(typeAdapter, (d2, typeIdx) -> {
                                if (typeIdx == 0) {
                                    store.add(AlertRule.buyOpportunity(assetName, ticker, profile));
                                    afterAdd();
                                } else if (typeIdx == 1) {
                                    store.add(AlertRule.sellReversal(assetName, ticker, profile));
                                    afterAdd();
                                } else {
                                    // Price level — ask for threshold
                                    showPriceThresholdDialog(assetName, ticker, typeIdx == 2);
                                }
                            }).show();
                }).show();
    }
    private void showPriceThresholdDialog(String assetName, String ticker, boolean above) {
        View inputView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_price_input, null);
        TextInputEditText et = inputView.findViewById(R.id.et_price_threshold);

        new MaterialAlertDialogBuilder(requireContext(), R.style.Widget_TradingApp_Dialog)
            .setTitle((above ? "Price Above" : "Price Below") + " — " + assetName)
            .setView(inputView)
            .setPositiveButton("Set Alert", (d, w) -> {
                String txt = et.getText() != null ? et.getText().toString().trim() : "";
                try {
                    double threshold = Double.parseDouble(txt);
                    AlertRule rule = above
                        ? AlertRule.priceAbove(assetName, ticker, threshold)
                        : AlertRule.priceBelow(assetName, ticker, threshold);
                    store.add(rule);
                    afterAdd();
                } catch (NumberFormatException e) {
                    Toast.makeText(getContext(), "Invalid price", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    private void afterAdd() {
        refreshList();
        Toast.makeText(getContext(), "Alert saved ✓", Toast.LENGTH_SHORT).show();
    }
    @Override public void onResume() { super.onResume(); refreshList(); }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
