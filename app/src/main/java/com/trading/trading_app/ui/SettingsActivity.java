package com.trading.trading_app.ui;

import android.os.Bundle;
import android.text.InputType;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference; import androidx.preference.PreferenceFragmentCompat;
import com.trading.app.R;
/** servers as settings*/
public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) { getSupportActionBar().setDisplayHomeAsUpEnabled(true); getSupportActionBar().setTitle("Settings"); }
        getSupportFragmentManager().beginTransaction().replace(R.id.settings_container, new SettingsFragment()).commit();
    }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            EditTextPreference capitalPref = findPreference("capital"); // Force numeric keyboard for capital input
            if (capitalPref != null) capitalPref.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL));
        }
    }
}
