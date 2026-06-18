package com.trading.trading_app;
import android.app.Application;
import androidx.annotation.NonNull;
import androidx.work.Configuration;
import com.trading.trading_app.worker.PriceRefreshWorker;
import com.trading.trading_app.worker.AlertWorker;
/**Application class — initialises WorkManager with custom config and schedules the periodic background price refresh.*/
public class TradingApplication extends Application implements Configuration.Provider {
    @Override
    public void onCreate() { super.onCreate(); PriceRefreshWorker.schedulePeriodicRefresh(this); AlertWorker.schedule(this); } // Schedule periodic background refresh (every 30 min when on Wi-Fi)
    @NonNull @Override
    public Configuration getWorkManagerConfiguration() { return new Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build(); }
}
