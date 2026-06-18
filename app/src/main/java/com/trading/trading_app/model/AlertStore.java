package com.trading.trading_app.model;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent storage for AlertRules using SharedPreferences + JSON.
 * No external database needed — alert count is small (< 50 typical).
 */
public class AlertStore {
    private static final String PREFS  = "trading_alerts";
    private static final String KEY    = "rules_json";
    private static AlertStore INSTANCE;

    private final SharedPreferences prefs;

    private AlertStore(Context ctx) {
        prefs = ctx.getApplicationContext()
                   .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static AlertStore getInstance(Context ctx) {
        if (INSTANCE == null) INSTANCE = new AlertStore(ctx);
        return INSTANCE;
    }

    public List<AlertRule> loadAll() {
        List<AlertRule> list = new ArrayList<>();
        String json = prefs.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                AlertRule r = fromJson(arr.getJSONObject(i));
                if (r != null) list.add(r);
            }
        } catch (JSONException ignored) {}
        return list;
    }

    public void saveAll(List<AlertRule> rules) {
        JSONArray arr = new JSONArray();
        for (AlertRule r : rules) arr.put(toJson(r));
        prefs.edit().putString(KEY, arr.toString()).apply();
    }

    public void add(AlertRule rule) {
        List<AlertRule> list = loadAll();
        list.add(rule);
        saveAll(list);
    }

    public void update(AlertRule rule) {
        List<AlertRule> list = loadAll();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(rule.id)) { list.set(i, rule); break; }
        }
        saveAll(list);
    }

    public void delete(String id) {
        List<AlertRule> list = loadAll();
        list.removeIf(r -> r.id.equals(id));
        saveAll(list);
    }

    // ── JSON serialisation ────────────────────────────────────────────
    private static JSONObject toJson(AlertRule r) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", r.id); o.put("assetName", r.assetName);
            o.put("ticker", r.ticker);
            o.put("profile", r.profile != null ? r.profile : "");
            o.put("type", r.type.name()); o.put("threshold", r.threshold);
            o.put("active", r.active); o.put("createdAt", r.createdAt);
            o.put("lastTriggeredAt", r.lastTriggeredAt);
            o.put("triggerCount", r.triggerCount);
        } catch (JSONException ignored) {}
        return o;
    }

    private static AlertRule fromJson(JSONObject o) {
        try {
            AlertRule r = new AlertRule();
            r.id = o.getString("id"); r.assetName = o.getString("assetName");
            r.ticker = o.optString("ticker", "");
            r.profile = o.optString("profile", "");
            r.type = AlertRule.Type.valueOf(o.getString("type"));
            r.threshold = o.getDouble("threshold"); r.active = o.getBoolean("active");
            r.createdAt = o.getLong("createdAt");
            r.lastTriggeredAt = o.optLong("lastTriggeredAt", 0);
            r.triggerCount = o.optInt("triggerCount", 0);
            return r;
        } catch (Exception e) { return null; }
    }
}
