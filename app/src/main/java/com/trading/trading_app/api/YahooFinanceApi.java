package com.trading.trading_app.api;
import retrofit2.Call; 
import retrofit2.http.GET; import retrofit2.http.Path; import retrofit2.http.Query;
/**Retrofit interface for the Yahoo Finance v8 chart endpoint (requests). Base URL: https://query1.finance.yahoo.com/
 * This endpoint returns OHLCV data in JSON — the same data that yfinance uses under the hood. No API key required.
 * Example: GET /v8/finance/chart/AAPL?interval=1d&range=6mo*/
public interface YahooFinanceApi {
    /**Fetch OHLCV history for a single ticker.
     * @param symbol   ticker symbol, e.g. "AAPL", "BTC-USD", "GC=F"
     * @param interval bar interval: "1m","5m","15m","30m","1h","1d" etc.
     * @param range    data range:   "5d","30d","180d","1y","2y","5y" etc.*/
    @GET("v8/finance/chart/{symbol}")
    Call<YahooChartResponse> getChart(@Path("symbol") String symbol, @Query("interval") String interval, @Query("range") String range, @Query("includePrePost") boolean includePrePost);
    /**Fetch a longer date-range by explicit start/end (unix seconds).*/
    @GET("v8/finance/chart/{symbol}")
    Call<YahooChartResponse> getChartDateRange(@Path("symbol") String symbol, @Query("interval") String interval, @Query("period1") long period1, @Query("period2") long period);
}
