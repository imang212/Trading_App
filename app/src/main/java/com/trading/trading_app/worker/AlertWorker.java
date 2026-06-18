package com.trading.trading_app.worker;

import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.PendingIntent;
import android.content.Context; import android.content.Intent;
import android.os.Build; import android.os.Handler; import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat; import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.LiveData; import androidx.lifecycle.Observer;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.trading.app.R;
import com.trading.trading_app.model.AlertEvaluator;
import com.trading.trading_app.model.AlertRule;
import com.trading.trading_app.model.AlertStore;
import com.trading.trading_app.model.Asset;
import com.trading.trading_app.repository.TradingRepository;
import com.trading.trading_app.ui.MainActivity;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
/**WorkManager Worker — runs every 15 minutes, fetches fresh data, evaluates all active alert rules, and fires Android notifications.
 * TradingRepository.fetchSignals() posts results to LiveData rather than a callback, so we observe it on the main thread via a Handler and use
 * a CountDownLatch to block the background Worker thread until data arrives (or times out after 30s).
 * Notification channels: trading_buy  — green, BUY OPPORTUNITY alerts,
 *                        trading_sell — red, SELL REVERSAL alerts,
 *                        trading_price — blue, price level alerts*/
public class AlertWorker extends Worker {
    private static final String TAG       = "AlertWorker";
    private static final String WORK_NAME = "alert_check_periodic";
    private static final String CH_BUY    = "trading_buy";
    private static final String CH_SELL   = "trading_sell";
    private static final String CH_PRICE  = "trading_price";
    public AlertWorker(@NonNull Context ctx, @NonNull WorkerParameters p) { super(ctx, p); }
    @NonNull @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        createChannels(ctx);

        List<AlertRule> rules = AlertStore.getInstance(ctx).loadAll();
        boolean anyActive = false;
        for (AlertRule r : rules) if (r.active) { anyActive = true; break; }
        if (!anyActive) return Result.success();

        List<Asset> assets = fetchSignalsBlocking(ctx);
        if (assets == null || assets.isEmpty()) { Log.w(TAG, "No assets returned"); return Result.retry(); }

        AlertStore store = AlertStore.getInstance(ctx);
        int notifId = 1000;

        for (AlertRule rule : rules) {
            if (!rule.active) continue;
            Asset asset = null;
            for (Asset a : assets) {
                if (a.name.equals(rule.assetName)) { asset = a; break; }
            }
            if (asset == null) continue;

            if (AlertEvaluator.evaluate(rule, asset)) {
                String reason = AlertEvaluator.reason(rule, asset);
                sendNotification(ctx, rule, asset, reason, notifId++);
                rule.lastTriggeredAt = System.currentTimeMillis();
                rule.triggerCount++;
                store.update(rule);
                Log.i(TAG, "Alert fired: " + rule.assetName + " — " + rule.typeLabel());
            }
        }
        return Result.success();
    }
    /**
     * Blocks the current (background) thread until TradingRepository's
     * signalsLd LiveData emits a value, observed on the main thread.
     * Times out after 30 seconds.
     */
    private List<Asset> fetchSignalsBlocking(Context ctx) {
        TradingRepository repo = TradingRepository.getInstance(ctx);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Asset>> result = new AtomicReference<>();
        LiveData<List<Asset>> liveData = repo.getSignals();

        Observer<List<Asset>> observer = new Observer<List<Asset>>() {
            @Override public void onChanged(List<Asset> assets) {
                if (assets == null || assets.isEmpty()) return; // wait for real data
                result.set(assets);
                new Handler(Looper.getMainLooper()).post(() -> liveData.removeObserver(this));
                latch.countDown();
            }
        };

        new Handler(Looper.getMainLooper()).post(() -> {
            liveData.observeForever(observer);
            repo.fetchSignals("1d", 10_000, null);
        });

        try { latch.await(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return result.get();
    }
    private void sendNotification(Context ctx, AlertRule rule, Asset asset, String reason, int id) {
        String channel = channelFor(rule.type);
        int color = colorFor(rule.type);

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_signals)
            .setContentTitle(rule.assetName + " — " + rule.typeLabel())
            .setContentText(reason)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(reason))
            .setColor(color)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setSubText(String.format("Price: %.2f", asset.price));

        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission not granted: " + e.getMessage());
        }
    }
    private void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
            CH_BUY, "BUY Opportunity Alerts", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(
            CH_SELL, "SELL Reversal Alerts", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(
            CH_PRICE, "Price Level Alerts", NotificationManager.IMPORTANCE_DEFAULT));
    }
    private String channelFor(AlertRule.Type t) {
        switch (t) {
            case BUY_OPPORTUNITY: return CH_BUY;
            case SELL_REVERSAL:   return CH_SELL;
            default:              return CH_PRICE;
        }
    }
    private int colorFor(AlertRule.Type t) {
        switch (t) {
            case BUY_OPPORTUNITY: return 0xFF2ECC71;
            case SELL_REVERSAL:   return 0xFFE74C3C;
            default:              return 0xFF4DA8E8;
        }
    }
    public static void schedule(Context ctx) {
        Constraints c = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
            AlertWorker.class, 15, TimeUnit.MINUTES)
            .setConstraints(c).build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req);
    }
    public static void cancel(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME);
    }
}
