package com.trading.trading_app.worker;

import android.content.Context; import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Constraints; import androidx.work.ExistingPeriodicWorkPolicy; import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest; import androidx.work.WorkManager; import androidx.work.Worker; import androidx.work.WorkerParameters;
import com.trading.trading_app.repository.TradingRepository;
import java.util.concurrent.TimeUnit;
/**Background WorkManager task: refreshes signal data every 30 minutes when the device has network connectivity.
 * Android 11+ battery optimisation is respected — WorkManager handles Doze mode automatically.*/
public class PriceRefreshWorker extends Worker {
    private static final String TAG = "PriceRefreshWorker", WORK_NAME = "price_refresh_periodic";
    public PriceRefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }
    @NonNull @Override
    public Result doWork() {
        try {
            Log.d(TAG, "Background price refresh starting…");
            TradingRepository repo = TradingRepository.getInstance(getApplicationContext());
            repo.clearCache();
            // Fetch the default daily interval for all assets in the background. The result is posted to LiveData; if the app is open, the UI updates automatically.
            repo.fetchSignals("1d", 10_000, null);
            Log.d(TAG, "Background refresh triggered.");
            return Result.success();
        } catch (Exception e) { Log.e(TAG, "Refresh failed: " + e.getMessage()); return Result.retry(); }
    }
    /** Schedule periodic refresh. Call from Application.onCreate(). */
    public static void schedulePeriodicRefresh(Context context) {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(PriceRefreshWorker.class, 30, TimeUnit.MINUTES).setConstraints(constraints).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request); // Don't reset if already scheduled
    }
}