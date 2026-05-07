package com.trading.trading_app.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;
/*Data answer parsing*/
public class YahooChartResponse {
    @SerializedName("chart") public ChartWrapper chart;
    public static class ChartWrapper {
        @SerializedName("result") public List<ChartResult> result;
        @SerializedName("error") public Object error;
    }
    public static class ChartResult {
        @SerializedName("meta") public Meta meta;
        @SerializedName("timestamp") public List<Long> timestamp;   // unix seconds
        @SerializedName("indicators") public Indicators indicators;
    }
    public static class Meta {
        @SerializedName("symbol") public String symbol;
        @SerializedName("currency") public String currency;
        @SerializedName("regularMarketPrice") public double regularMarketPrice;
        @SerializedName("chartPreviousClose") public double chartPreviousClose;
    }
    public static class Indicators {
        @SerializedName("quote") public List<Quote> quote;
        @SerializedName("adjclose") public List<AdjClose> adjclose;
    }
    public static class Quote {
        @SerializedName("open") public List<Double> open;
        @SerializedName("high") public List<Double> high;
        @SerializedName("low") public List<Double> low;
        @SerializedName("close") public List<Double> close;
        @SerializedName("volume") public List<Long> volume;
    }
    public static class AdjClose {
        @SerializedName("adjclose") public List<Double> adjclose;
    }
    // Helper: extract first valid ChartResult or null
    public ChartResult firstResult() {
        if (chart == null || chart.result == null || chart.result.isEmpty()) return null;
        return chart.result.get(0);
    }
    public boolean hasError() { return chart == null || chart.error != null || chart.result == null || chart.result.isEmpty(); }
}
