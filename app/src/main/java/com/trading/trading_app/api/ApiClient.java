package com.trading.trading_app.api;

import android.content.Context;
import java.io.File;
import java.util.concurrent.TimeUnit;
import okhttp3.Cache; import okhttp3.OkHttpClient; import okhttp3.Request; import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit; import retrofit2.converter.gson.GsonConverterFactory;
/**Singleton Retrofit client for Yahoo Finance (instance).
 * Features:
 * - 10 MB disk cache → faster repeat requests, works partially offline
 * - Gzip automatically handled by OkHttp
 * - 20-second connect/read timeout suitable for mobile
 * - Fake browser User-Agent (Yahoo blocks default Java UA)
 * - Mirror URL fallback (query2) if query1 is blocked*/
public final class ApiClient {
    private static final String BASE_URL  = "https://query1.finance.yahoo.com/", BASE_URL2 = "https://query2.finance.yahoo.com/";
    private static final long CACHE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static volatile YahooFinanceApi instance, fallback;
    private ApiClient() {}
    public static YahooFinanceApi get(Context ctx) {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) { instance = build(ctx, BASE_URL); fallback  = build(ctx, BASE_URL2); }
            }
        }
        return instance;
    }
    public static YahooFinanceApi getFallback(Context ctx) {
        if (fallback == null) get(ctx);
        return fallback;
    }
    private static YahooFinanceApi build(Context ctx, String baseUrl) {
        File cacheDir = new File(ctx.getCacheDir(), "http_cache");
        Cache cache = new Cache(cacheDir, CACHE_SIZE);
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor(); logger.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder().cache(cache).connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(chain -> {
                // Add browser-like headers so Yahoo doesn't reject the request
                Request original = chain.request();
                Request modified = original.newBuilder()
                    .header("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache").build();
                return chain.proceed(modified);
            }).addInterceptor(logger)
            // Cache-control: only cache successful JSON responses
            .addNetworkInterceptor(chain -> {
                okhttp3.Response response = chain.proceed(chain.request());
                String contentType = response.header("Content-Type");
                if (response.isSuccessful() && contentType != null && contentType.contains("application/json")) {
                    return response.newBuilder().header("Cache-Control", "public, max-age=1800").build();
                }
                else {
                    return response.newBuilder().header("Cache-Control", "no-store").build();
                }
            }).build();
        return new Retrofit.Builder().baseUrl(baseUrl).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(YahooFinanceApi.class);
    }
}