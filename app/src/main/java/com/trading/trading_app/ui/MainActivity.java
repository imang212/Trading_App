package com.trading.trading_app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu; import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.trading.app.R;
import com.trading.app.databinding.ActivityMainBinding;
/**Single Activity host. Navigation handled by Navigation Component (switching between fragments). Bottom nav tabs: Signals | Detail | Orders | Backtest | Correlation*/
public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding; private NavController navController;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        NavHostFragment navHostFragment = (NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();
        AppBarConfiguration appBarConfig = new AppBarConfiguration.Builder(R.id.nav_signals, R.id.nav_orders, R.id.nav_backtest, R.id.nav_correlation).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig);
        NavigationUI.setupWithNavController(binding.bottomNav, navController);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) { getMenuInflater().inflate(R.menu.menu_main, menu); return true; }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_refresh) {
            sendBroadcast(new Intent("com.trading.app.REFRESH")); // Post a broadcast that fragments listen to
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public boolean onSupportNavigateUp() { return navController.navigateUp() || super.onSupportNavigateUp(); }
}
