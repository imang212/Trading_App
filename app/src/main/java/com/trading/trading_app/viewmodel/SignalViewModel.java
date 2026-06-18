package com.trading.trading_app.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel; import androidx.lifecycle.LiveData; import androidx.lifecycle.MediatorLiveData; import androidx.lifecycle.MutableLiveData;
import com.trading.trading_app.model.Asset;
import com.trading.trading_app.repository.TradingRepository;
import java.util.ArrayList; import java.util.Collections; import java.util.List; import java.util.Set;
import com.trading.trading_app.ui.overview.SignalAdapter;
/**ViewModel for Signal Overview screen. Survives configuration changes; exposes filtered LiveData to the Fragment. Keeps live data on UI fragments*/
public class SignalViewModel extends AndroidViewModel {
    private final TradingRepository repo;
    // Filters (mutable state held in ViewModel)
    private final MutableLiveData<String> intervalLd = new MutableLiveData<>("1d");
    private final MutableLiveData<Double> capitalLd = new MutableLiveData<>(10_000.0);
    private final MutableLiveData<Long> startDateLd = new MutableLiveData<>(1514764800000L);  // 0 = use default 5yr
    private final MutableLiveData<Boolean> convertCurrencyLd = new MutableLiveData<>(true);
    private final MutableLiveData<Set<String>> sigFilterLd = new MutableLiveData<>(), profFilterLd = new MutableLiveData<>();
    private final MediatorLiveData<List<Asset>> filteredSignals = new MediatorLiveData<>(); // Filtered output
    private List<Asset> allSignals = new ArrayList<>();
    private SignalAdapter.SortOrder sortOrder = SignalAdapter.SortOrder.DEFAULT;
    public SignalViewModel(@NonNull Application application) {
        super(application);
        repo = TradingRepository.getInstance(application); // Re-apply filter whenever raw signals change
        filteredSignals.addSource(repo.getSignals(), signals -> { allSignals = signals != null ? signals : new ArrayList<>(); applyFilter(); });
    }
    // Outputs
    public LiveData<List<Asset>> getFilteredSignals() { return filteredSignals; }
    public LiveData<List<Asset>> getAllSignals() { return repo.getSignals(); }
    public LiveData<Boolean> isLoading() { return repo.isLoading(); }
    public LiveData<String> getError() { return repo.getError(); }
    // Inputs
    public void refresh() {
        repo.clearCache();
        repo.fetchSignals(getInterval(), getCapital(), null);
    }
    public void loadIfEmpty() {
        List<Asset> current = repo.getSignals().getValue();
        if (current == null || current.isEmpty()) refresh();
    }
    public void setInterval(String interval) { intervalLd.setValue(interval); repo.clearCache(); reload(); }
    public void setCapital(double capital) { capitalLd.setValue(capital); repo.clearCache(); reload(); }
    public void setStartDate(long epochMs) { startDateLd.setValue(epochMs); repo.clearCache(); reload(); }
    public void setConvertCurrency(boolean convert) { convertCurrencyLd.setValue(convert); repo.setConvertCurrency(convert); repo.clearCache(); reload(); }
    public void setSignalFilter(Set<String> signals) { sigFilterLd.setValue(signals); applyFilter(); }
    public void setProfileFilter(Set<String> profiles) { profFilterLd.setValue(profiles); applyFilter(); }
    public SignalAdapter.SortOrder getSortOrder() { return sortOrder; }
    public void setSortOrder(SignalAdapter.SortOrder order) { sortOrder = order; applyFilter(); }
    public String getInterval() { return intervalLd.getValue() != null ? intervalLd.getValue() : "1d"; }
    public double getCapital() { return capitalLd.getValue() != null ? capitalLd.getValue() : 10_000; }
    public long getStartDate() { return startDateLd.getValue() != null ? startDateLd.getValue() : 0L; }
    public boolean isConvertCurrency() { return convertCurrencyLd.getValue() != null && convertCurrencyLd.getValue(); }
    // Reload without clearing cache
    private void reload() { repo.fetchSignals(getInterval(), getCapital(), null); }
    // Filter logic
    private void applyFilter() {
        Set<String> sigF = sigFilterLd.getValue(), profF = profFilterLd.getValue();
        if ((sigF == null || sigF.size() == 3) && (profF == null || profF.size() == 5)) {
            filteredSignals.setValue(sortedCopy(allSignals)); return;
        }
        List<Asset> out = new ArrayList<>();
        for (Asset a : allSignals) {
            boolean sigOk = sigF == null || sigF.contains(a.signal);
            boolean profOk = profF == null || profF.contains(a.profile);
            if (sigOk && profOk) out.add(a);
        }
        filteredSignals.setValue(sortedCopy(out));
    }
    private List<Asset> sortedCopy(List<Asset> list) {
        List<Asset> copy = new ArrayList<>(list);
        if (sortOrder == SignalAdapter.SortOrder.BAYES_DESC) Collections.sort(copy, (a, b) -> Double.compare(b.bayesBuyScore, a.bayesBuyScore));
        else if (sortOrder == SignalAdapter.SortOrder.BAYES_ASC) Collections.sort(copy, (a, b) -> Double.compare(a.bayesBuyScore, b.bayesBuyScore));
        return copy;
    }

}