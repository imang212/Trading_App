package com.trading.trading_app.ui.overview;

import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter;
import android.graphics.Color; import android.os.Build; import android.os.Bundle;
import android.text.Editable; import android.text.TextWatcher;
import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup;
import android.widget.AdapterView; import android.widget.ArrayAdapter;

import androidx.annotation.NonNull; import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.chip.Chip; import com.google.android.material.datepicker.MaterialDatePicker;
import com.trading.app.R;
import com.trading.app.databinding.FragmentSignalOverviewBinding;
import com.trading.trading_app.model.Asset;
import com.trading.trading_app.model.AppConfig;
import com.trading.trading_app.ui.detail.AssetDetailFragment;
import com.trading.trading_app.viewmodel.SignalViewModel;
import java.text.SimpleDateFormat; import java.util.Arrays; import java.util.Date; import java.util.HashSet; import java.util.Locale; import java.util.Set;
/**Main screen: signal cards + filter chips + interval spinner. Mirrors the "Signal Overview" page of the Streamlit dashboard.*/
public class SignalOverviewFragment extends Fragment {
    private FragmentSignalOverviewBinding binding; private SignalViewModel vm; private SignalAdapter adapter;
    private final Set<String> activeSignalFilters  = new HashSet<>(Arrays.asList("BUY","SELL","NEU")), activeProfileFilters = new HashSet<>(AppConfig.ALL_PROFILES);
    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() { @Override public void onReceive(Context ctx, Intent i) { vm.refresh(); } };
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSignalOverviewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(SignalViewModel.class);
        setupIntervalSpinner(); setupFilterChips(); setupRecyclerView(); setupSummaryCards(); setupSwipeRefresh(); setupPortfolioSettings(); observeViewModel();
        vm.loadIfEmpty();
    }
    private void setupPortfolioSettings() {
        binding.etInitialCapital.addTextChangedListener(new TextWatcher() { // 1. Initial Capital — update ViewModel when user finishes typing
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                if (!text.isEmpty()) {
                    try {
                        double capital = Double.parseDouble(text);
                        if (capital > 0 && capital != vm.getCapital()) vm.setCapital(capital);
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
        binding.etInitialCapital.setText(String.valueOf((int) vm.getCapital())); // Pre-fill with current ViewModel value
        binding.btnPickDate.setOnClickListener(v -> { // Backtest start date picker
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker().setTheme(R.style.TradingApp_DatePicker).setTitleText("Select Backtest Start Date").setSelection(vm.getStartDate() > 0 ? vm.getStartDate() : MaterialDatePicker.todayInUtcMilliseconds() - 5L * 365 * 24 * 3600 * 1000).build();
            picker.addOnPositiveButtonClickListener(epochMs -> {
                vm.setStartDate(epochMs);
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                binding.tvSelectedDate.setText("Backtest from: " + sdf.format(new Date(epochMs))); binding.tvSelectedDate.setTextColor(requireContext().getColor(R.color.accent_blue));
            });
            picker.show(getParentFragmentManager(), "DATE_PICKER");
        });
        binding.switchCurrencyConvert.setChecked(vm.isConvertCurrency()); // Currency conversion switch
        binding.switchCurrencyConvert.setOnCheckedChangeListener((btn, checked) -> {
            vm.setConvertCurrency(checked);
            binding.tvCurrencyDetected.setText(checked ? "Converting non-USD assets → USD automatically" : "Showing prices in native asset currency");
        });
        binding.tvCurrencyDetected.setText(vm.isConvertCurrency() ? "Converting non-USD assets → USD automatically" : "Showing prices in native asset currency"); // Set initial label
    }
    // Interval spinner
    private void setupIntervalSpinner() {
        String[] intervals = {"1d","4h","1h","30m","15m","5m"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.spinner_item, intervals);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        binding.spinnerInterval.setAdapter(adapter);
        String current = vm.getInterval(); // Sync selection with ViewModel
        for (int i = 0; i < intervals.length; i++) if (intervals[i].equals(current)) { binding.spinnerInterval.setSelection(i); break; }
        binding.spinnerInterval.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!intervals[pos].equals(vm.getInterval())) vm.setInterval(intervals[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }
    // Filter chips (signal + profile)
    private void setupFilterChips() {
        // Signal filter chips
        for (Chip chip : new Chip[]{binding.chipBuy, binding.chipSell, binding.chipNeu}) {
            chip.setOnCheckedChangeListener((v, checked) -> {
                String tag = (String) v.getTag();
                if (checked) activeSignalFilters.add(tag); else activeSignalFilters.remove(tag);
                vm.setSignalFilter(new HashSet<>(activeSignalFilters));
            });
        }
        // Profile filter chips
        for (int i = 0; i < binding.chipGroupProfile.getChildCount(); i++) {
            Chip c = (Chip) binding.chipGroupProfile.getChildAt(i);
            c.setOnCheckedChangeListener((v, checked) -> {
                String tag = (String) v.getTag();
                if (checked) activeProfileFilters.add(tag); else activeProfileFilters.remove(tag);
                vm.setProfileFilter(new HashSet<>(activeProfileFilters));
            });
        }
    }
    // RecyclerView
    private void setupRecyclerView() {
        adapter = new SignalAdapter(asset -> {
            // Navigate to detail screen
            Bundle args = new Bundle();
            args.putString(AssetDetailFragment.ARG_ASSET_NAME, asset.name);
            args.putString(AssetDetailFragment.ARG_INTERVAL, vm.getInterval());
            Navigation.findNavController(requireView()).navigate(R.id.action_signals_to_detail, args);
        });
        binding.recyclerSignals.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerSignals.setAdapter(adapter);
        binding.recyclerSignals.setHasFixedSize(false);
        // Improve scroll performance
        binding.recyclerSignals.setItemViewCacheSize(20);
    }
    // Summary cards (BUY / SELL counts)
    private void setupSummaryCards() {
        vm.getAllSignals().observe(getViewLifecycleOwner(), signals -> {
            if (signals == null) return;
            int buys  = 0, sells = 0, neus = 0;
            for (Asset a : signals) {
                if (a.isBuy())  buys++; else if (a.isSell()) sells++; else neus++;
            }
            // Update Total Card
            binding.cardTotal.tvStatValue.setText(String.valueOf(signals.size()));
            binding.cardTotal.tvStatLabel.setText("Total Assets");
            // Update Buy Card
            binding.cardBuy.tvStatValue.setText(String.valueOf(buys));
            binding.cardBuy.tvStatLabel.setText("BUY Signals");
            binding.cardBuy.tvStatValue.setTextColor(Color.parseColor("#2ECC71"));
            // Update Sell Card
            binding.cardSell.tvStatValue.setText(String.valueOf(sells));
            binding.cardSell.tvStatLabel.setText("SELL Signals");
            binding.cardSell.tvStatValue.setTextColor(Color.parseColor("#E74C3C"));
            // Update Neu Card
            binding.cardNeu.tvStatValue.setText(String.valueOf(neus));
            binding.cardNeu.tvStatLabel.setText("NEUTRAL");
            binding.cardNeu.tvStatValue.setTextColor(Color.parseColor("#F39C12"));
        });
    }
    // Swipe-to-refresh
    private void setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.buy_green, R.color.accent_blue);
        binding.swipeRefresh.setOnRefreshListener(vm::refresh);
    }
    // Observe ViewModel
    private void observeViewModel() {
        vm.getFilteredSignals().observe(getViewLifecycleOwner(), signals -> {
            adapter.submitList(signals);
            binding.tvEmptyState.setVisibility(signals == null || signals.isEmpty() ? View.VISIBLE : View.GONE);
        });
        vm.isLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.swipeRefresh.setRefreshing(loading);
            binding.shimmerLayout.setVisibility(loading && (adapter.getItemCount() == 0) ? View.VISIBLE : View.GONE);
            if (loading) binding.shimmerLayout.startShimmer();
            else binding.shimmerLayout.stopShimmer();
        });
        vm.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) { binding.tvError.setVisibility(View.VISIBLE); binding.tvError.setText("⚠ " + error); }
            else { binding.tvError.setVisibility(View.GONE); }
        });
    }
    @Override public void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(refreshReceiver, new IntentFilter("com.trading.app.REFRESH"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            ContextCompat.registerReceiver(requireContext(), refreshReceiver, new IntentFilter("com.trading.app.REFRESH"), ContextCompat.RECEIVER_NOT_EXPORTED);
        }
    }
    @Override public void onStop() { super.onStop(); requireContext().unregisterReceiver(refreshReceiver); }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
