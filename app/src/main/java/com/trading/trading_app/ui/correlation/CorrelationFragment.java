package com.trading.trading_app.ui.correlation;

import android.graphics.Color; import android.os.Bundle;
import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup; import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull; import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.trading.app.R;
import androidx.lifecycle.ViewModelProvider;
import com.trading.app.databinding.FragmentCorrelationBinding;
import com.trading.trading_app.model.AppConfig;
import com.trading.trading_app.model.Asset; import com.trading.trading_app.model.Asset.OhlcBar;
import com.trading.trading_app.util.TechnicalIndicators;
import com.trading.trading_app.viewmodel.SignalViewModel;
import java.util.ArrayList; import java.util.Arrays; import java.util.Collections; import java.util.HashSet; import java.util.List; import java.util.Set;
/** Correlation Matrix screen. Uses Pearson correlation of daily % returns. Allows user to select up to 10 assets to compare.*/
public class CorrelationFragment extends Fragment {
    private FragmentCorrelationBinding binding; private SignalViewModel vm;
    private final List<String> selectedAssets = new ArrayList<>(Arrays.asList("Gold", "Brent_Oil", "USDIDX", "Bitcoin", "MSFT", "GOOGL", "CocaColaCCH", "AgnicoEagle", "Moneta", "Nokia"));
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCorrelationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(SignalViewModel.class);
        binding.btnSelectAssets.setOnClickListener(v -> showAssetSelectionDialog());
        vm.getAllSignals().observe(getViewLifecycleOwner(), signals -> {
            if (signals == null || signals.isEmpty()) return;
            updateMatrix(signals);
        });
    }
    private void showAssetSelectionDialog() {
        List<String> allAssetNames = new ArrayList<>(AppConfig.ASSETS.keySet());
        Collections.sort(allAssetNames);
        String[] items = allAssetNames.toArray(new String[0]);
        boolean[] checkedItems = new boolean[items.length];
        Set<String> currentlySelected = new HashSet<>(selectedAssets);
        for (int i = 0; i < items.length; i++) checkedItems[i] = currentlySelected.contains(items[i]);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), com.trading.app.R.style.Widget_TradingApp_Dialog)
                .setTitle("Select Assets (Max 10)")
                .setMultiChoiceItems(items, checkedItems, (dig, which, isChecked) -> {
                    String name = items[which];
                    if (isChecked) {
                        if (selectedAssets.size() >= 10) {
                            ((AlertDialog) dig).getListView().setItemChecked(which, false);
                            checkedItems[which] = false;
                            Toast.makeText(getContext(), "Maximum 10 assets allowed", Toast.LENGTH_SHORT).show();
                        } else {
                            if (!selectedAssets.contains(name)) selectedAssets.add(name);
                        }
                    } else {
                        selectedAssets.remove(name);
                    }
                })
                .setPositiveButton("OK", (dig, which) -> {
                    List<Asset> all = vm.getAllSignals().getValue();
                    if (all != null) updateMatrix(all); }).setNegativeButton("Cancel", null).show();
        dialog.show();
        ListView lv = dialog.getListView(); // device's system alert dialog implementation.
        if (lv != null) {
            lv.setBackgroundColor(Color.parseColor("#1A1D2E"));   // bg_card
            lv.setDivider(null); lv.setDividerHeight(0);
            // Re-colour all visible items immediately after dialog is shown
            lv.post(() -> {
                int count = lv.getChildCount();
                for (int i = 0; i < count; i++) {
                    View child = lv.getChildAt(i);
                    if (child instanceof android.widget.CheckedTextView) {
                        android.widget.CheckedTextView ctv = (android.widget.CheckedTextView) child;
                        ctv.setTextColor(Color.parseColor("#F0F2FF"));
                        ctv.setCheckMarkTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4DA8E8")));
                    }
                }
            });
            // Also handle items that scroll into view later
            lv.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                int count = lv.getChildCount();
                for (int i = 0; i < count; i++) {
                    View child = lv.getChildAt(i);
                    if (child instanceof android.widget.CheckedTextView) {
                        android.widget.CheckedTextView ctv = (android.widget.CheckedTextView) child;
                        ctv.setTextColor(Color.parseColor("#F0F2FF"));
                        ctv.setCheckMarkTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4DA8E8")));
                    }
                }
            });
        }
    }
    private void updateMatrix(List<Asset> signals) {
        // Filter signals to only include selected ones and those with enough history
        List<Asset> toProcess = new ArrayList<>();
        for (String name : selectedAssets) {
            for (Asset a : signals) {
                if (a.name.equals(name) && a.ohlcHistory != null && a.ohlcHistory.size() > 5) { toProcess.add(a); break; }
            }
        }
        int n = toProcess.size();
        if (n < 2) {
            binding.correlationGridView.setVisibility(View.GONE);
            binding.tvDiversification.setText("Select at least 2 assets with data.");
            return;
        }
        // Extract daily close arrays → compute % returns
        double[][] rets = new double[n][]; String[] names = new String[n];
        int minLen = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            List<OhlcBar> bars = toProcess.get(i).ohlcHistory;
            double[] prices = new double[bars.size()];
            for (int j = 0; j < bars.size(); j++) prices[j] = bars.get(j).close;
            rets[i] = TechnicalIndicators.pctReturns(prices);
            names[i] = toProcess.get(i).name;
            if (rets[i].length < minLen) minLen = rets[i].length;
        }
        // Trim all to same length and cap at 252 days (approx 1 trading year)
        int finalLen = Math.min(minLen, 252);
        double[][] corr = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double[] xi = trimTo(rets[i], finalLen);
                double[] xj = trimTo(rets[j], finalLen);
                corr[i][j] = i == j ? 1.0 : TechnicalIndicators.pearsonCorr(xi, xj);
            }
        }
        binding.correlationGridView.setData(names, corr);
        binding.correlationGridView.setVisibility(View.VISIBLE);
        // Diversification score
        double avgAbs = 0, count = 0;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) { avgAbs += Math.abs(corr[i][j]); count++; }
        avgAbs = count > 0 ? avgAbs / count : 0;
        String div = avgAbs < 0.30 ? "Excellent" : avgAbs < 0.50 ? "Good" : avgAbs < 0.65 ? "Moderate" : "Poor";
        binding.tvDiversification.setText(String.format("Diversification: %s  (avg |corr| %.2f)", div, avgAbs));
        binding.tvDiversification.setTextColor(avgAbs < 0.50 ? Color.parseColor("#2ECC71") : avgAbs < 0.65 ? Color.parseColor("#F39C12") : Color.parseColor("#E74C3C"));
    }
    private double[] trimTo(double[] arr, int len) {
        if (arr.length <= len) return arr;
        double[] out = new double[len];
        System.arraycopy(arr, arr.length - len, out, 0, len);
        return out;
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
