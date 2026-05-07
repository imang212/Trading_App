package com.trading.trading_app.ui.backtest;

import android.graphics.Color; import android.os.Bundle;
import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull; import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.github.mikephil.charting.data.Entry; import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.trading.app.R;
import com.trading.app.databinding.FragmentBacktestBinding;
import com.trading.trading_app.model.Asset;
import com.trading.trading_app.model.BacktestResult;
import com.trading.trading_app.repository.TradingRepository;
import com.trading.trading_app.viewmodel.SignalViewModel;
import java.text.NumberFormat; import java.text.SimpleDateFormat;
import java.util.ArrayList; import java.util.Date; import java.util.List; import java.util.Locale;
/**Backtest Summary screen. Runs an on-device backtest for the currently selected asset and shows:
 *  - Return, B&H return, Alpha, Win rate, Sharpe, Max DD
 *  - Equity curve (MPAndroidChart LineChart)*/
public class BacktestFragment extends Fragment {
    private FragmentBacktestBinding binding; private SignalViewModel vm;
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBacktestBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(SignalViewModel.class);
        setupAssetSpinner(); setupCapitalInput(); setupDatePicker(); setupRunButton(); setupEquityChart();
    }
    private void setupAssetSpinner() {
        vm.getAllSignals().observe(getViewLifecycleOwner(), signals -> {
            if (signals == null || signals.isEmpty()) return;
            List<String> names = new ArrayList<>();
            for (Asset a : signals) names.add(a.name);
            // Use our dark spinner layouts instead of system light ones
            ArrayAdapter<String> adapter = new ArrayAdapter<>( requireContext(), R.layout.spinner_item, names);
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
            binding.spinnerBacktestAsset.setAdapter(adapter);
        });
    }
    private void setupCapitalInput() {
        binding.etBacktestCapital.setText(String.valueOf((int) vm.getCapital())); // Pre-fill with whatever capital is already in ViewModel
        binding.etBacktestCapital.addTextChangedListener( new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String t = s.toString().trim();
                if (!t.isEmpty()) {
                    try { double cap = Double.parseDouble(t); if (cap > 0) vm.setCapital(cap);}
                    catch (NumberFormatException ignored) {}
                }
            }
        });
    }
    private void setupDatePicker() {
        long existing = vm.getStartDate(); // Show already-set date if any
        if (existing > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            binding.tvBacktestDate.setText(sdf.format(new Date(existing)));
            binding.tvBacktestDate.setTextColor(requireContext().getColor(R.color.accent_blue));
        }
        binding.btnBacktestPickDate.setOnClickListener(v -> {
            long init = vm.getStartDate() > 0 ? vm.getStartDate() : MaterialDatePicker.todayInUtcMilliseconds() - 5L * 365 * 24 * 3600 * 1000;
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker().setTheme(R.style.TradingApp_DatePicker).setTitleText("Backtest Start Date").setSelection(init).build();
            picker.addOnPositiveButtonClickListener(epochMs -> {
                vm.setStartDate(epochMs);
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                binding.tvBacktestDate.setText(sdf.format(new Date(epochMs)));
                binding.tvBacktestDate.setTextColor(requireContext().getColor(R.color.accent_blue));
            });
            picker.show(getParentFragmentManager(), "BT_DATE_PICKER");
        });
    }
    private void setupRunButton() {
        binding.btnRunBacktest.setOnClickListener(v -> {
            String selected = (String) binding.spinnerBacktestAsset.getSelectedItem();
            if (selected == null) return;
            runBacktest(selected);
        });
    }
    private void runBacktest(String assetName) {
        binding.progressBacktest.setVisibility(View.VISIBLE);
        binding.cardBacktestResult.setVisibility(View.GONE);
        binding.tvBacktestError.setVisibility(View.GONE);
        binding.btnRunBacktest.setEnabled(false);
        double capital = vm.getCapital();
        long startDate = vm.getStartDate();
        TradingRepository.getInstance(requireContext()).runBacktest(assetName, capital, startDate, new TradingRepository.BacktestCallback() {
                @Override public void onSuccess(BacktestResult bt) { requireActivity().runOnUiThread(() -> displayResult(bt)); }
                @Override public void onError(String msg) { requireActivity().runOnUiThread(() -> {
                        binding.progressBacktest.setVisibility(View.GONE);
                        binding.btnRunBacktest.setEnabled(true);
                        binding.tvBacktestError.setVisibility(View.VISIBLE);
                        binding.tvBacktestError.setText("Error: " + msg);
                    });
                }
        });
    }
    private void displayResult(BacktestResult bt) {
        binding.progressBacktest.setVisibility(View.GONE);
        binding.btnRunBacktest.setEnabled(true);
        binding.cardBacktestResult.setVisibility(View.VISIBLE);
        binding.tvBacktestError.setVisibility(View.GONE);
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.US);
        binding.tvBtAsset.setText(bt.asset + "  [" + bt.profile + "]");
        binding.tvBtFinal.setText(nf.format(bt.finalValue));
        binding.tvBtReturn.setText(String.format(Locale.US, "%+.1f%%", bt.totalReturn));
        binding.tvBtReturn.setTextColor(bt.totalReturn >= 0 ? Color.parseColor("#2ECC71") : Color.parseColor("#E74C3C"));
        binding.tvBtBh.setText(String.format(Locale.US, "%+.1f%%", bt.bhReturn));
        binding.tvBtAlpha.setText(String.format(Locale.US, "%+.1f%%", bt.alpha));
        binding.tvBtAlpha.setTextColor(bt.alpha >= 0 ? Color.parseColor("#2ECC71") : Color.parseColor("#E74C3C"));
        binding.tvBtWinRate.setText(String.format(Locale.US, "%.0f%%", bt.winRate));
        binding.tvBtSharpe.setText(String.format(Locale.US, "%.2f", bt.sharpe));
        binding.tvBtSharpe.setTextColor(bt.sharpe >= 1 ? Color.parseColor("#2ECC71") : bt.sharpe >= 0 ? Color.parseColor("#F39C12") : Color.parseColor("#E74C3C"));
        binding.tvBtMaxDd.setText(String.format(Locale.US, "%.1f%%", bt.maxDrawdown));
        binding.tvBtTrades.setText(String.format(Locale.US, "%d  (W:%d / L:%d)", bt.totalTrades, bt.winningTrades, bt.losingTrades));
        if (bt.beatsHold()) { binding.tvBtAlphaBadge.setText("✔ Beats Buy & Hold"); binding.tvBtAlphaBadge.setTextColor(Color.parseColor("#2ECC71")); }
        else { binding.tvBtAlphaBadge.setText("✕ Underperforms B&H"); binding.tvBtAlphaBadge.setTextColor(Color.parseColor("#E74C3C")); }
        if (bt.equityCurve != null && !bt.equityCurve.isEmpty()) {
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < bt.equityCurve.size(); i++) entries.add(new Entry(i, (float) bt.equityCurve.get(i).equity));
            LineDataSet ds = new LineDataSet(entries, bt.asset + " equity");
            int lineColor = bt.totalReturn >= 0 ? Color.parseColor("#2ECC71") : Color.parseColor("#E74C3C");
            ds.setColor(lineColor); ds.setLineWidth(2f); ds.setDrawCircles(false); ds.setDrawValues(false); ds.setFillAlpha(40);
            ds.setFillColor(lineColor); ds.setDrawFilled(true);
            binding.equityChart.setData(new LineData(ds));
            binding.equityChart.getDescription().setText("Equity  (initial " + nf.format(bt.initialCapital) + ")");
            binding.equityChart.getDescription().setTextColor(Color.LTGRAY);
            binding.equityChart.invalidate();
        }
    }
    private void setupEquityChart() {
        binding.equityChart.setBackgroundColor(Color.parseColor("#1E2130"));
        binding.equityChart.getDescription().setEnabled(true);
        binding.equityChart.getLegend().setTextColor(Color.LTGRAY);
        binding.equityChart.getAxisLeft().setTextColor(Color.LTGRAY);
        binding.equityChart.getAxisLeft().setGridColor(Color.parseColor("#2A2D3E"));
        binding.equityChart.getAxisRight().setEnabled(false);
        binding.equityChart.getXAxis().setTextColor(Color.LTGRAY);
        binding.equityChart.getXAxis().setGridColor(Color.parseColor("#2A2D3E"));
        binding.equityChart.setNoDataText("Run a backtest to see the equity curve");
        binding.equityChart.setNoDataTextColor(Color.LTGRAY);
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}