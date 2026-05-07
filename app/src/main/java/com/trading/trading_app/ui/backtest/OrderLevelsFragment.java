package com.trading.trading_app.ui.backtest;

import android.os.Bundle;
import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup;

import androidx.annotation.NonNull; import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.trading.app.databinding.FragmentOrderLevelsBinding;
import com.trading.trading_app.viewmodel.SignalViewModel;
/** Order Levels screen — mirrors the "Order Levels" page of the Streamlit dashboard. Shows Buy Limit, Stop-Loss, TP1, TP2, Risk/trade for every asset.*/
public class OrderLevelsFragment extends Fragment {
    private FragmentOrderLevelsBinding binding; private SignalViewModel vm; private OrderLevelsAdapter adapter;
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentOrderLevelsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(SignalViewModel.class);
        adapter = new OrderLevelsAdapter();
        binding.recyclerOrders.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerOrders.setAdapter(adapter);
        binding.recyclerOrders.setHasFixedSize(true);
        vm.getFilteredSignals().observe(getViewLifecycleOwner(), signals -> {
            if (signals == null) return;
            adapter.submitList(signals);
            binding.tvOrdersCaption.setText(String.format("Buy Limit = BB lower + 0.5%%  ·  SL = BuyLimit − ATR×mult  ·  %d assets", signals.size()));
        });
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
