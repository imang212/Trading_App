package com.trading.trading_app.model;
import java.util.Arrays; import java.util.Collections; import java.util.HashMap; import java.util.LinkedHashMap; import java.util.List; import java.util.Map;
/**Central configuration — mirrors ASSETS, PROFILES, ASSET_PROFILES and INTERVAL_SETTINGS from trading_backtest_script.py.
 * Keeping everything in one Java class allows the app to work offline (no server needed to know the asset list) and gives a single place to add/remove assets.*/
public final class AppConfig {
    private AppConfig() {}
    // Intervals
    public enum Interval {
        M1("1m", "1 min", "5d"), M5("5m", "5 min", "30d"), M15("15m","15 min","30d"),
        M30("30m","30 min","30d"), H1("1h", "1 hour", "180d"), H4("4h", "4 hours","180d"),
        D1("1d", "Daily",  "1y");
        public final String yf, label, period; // yFinance interval string;
        Interval(String yf, String label, String period) { this.yf = yf; this.label = label; this.period = period;}
        public static Interval fromYf(String yf) {
            for (Interval i : values()) if (i.yf.equals(yf)) return i;
            return D1;
        }
    }
    // Profiles
    public enum Profile {
        COMMODITY ("COMMODITY"), CRYPTO ("CRYPTO"), FOREX_IDX ("FOREX_IDX"), TECH ("TECH"), DEFENSIVE ("DEFENSIVE");
        public final String key;
        Profile(String key) { this.key = key; }
    }
    /** Profile parameters (mirrors PROFILES dict) */
    public static class ProfileParams {
        public final int maShort, maLong, rsiPeriod, rsiOB, rsiOS, bbPeriod;
        public final double bbStd;
        public final int macdFast, macdSlow, macdSignal, atrPeriod;
        public final double atrSlMult;
        ProfileParams(int maShort, int maLong, int rsiPeriod, int rsiOB, int rsiOS, int bbPeriod, double bbStd, int macdFast, int macdSlow, int macdSignal, int atrPeriod, double atrSlMult) {
            this.maShort = maShort; this.maLong = maLong; this.rsiPeriod = rsiPeriod; this.rsiOB = rsiOB; this.rsiOS = rsiOS; this.bbPeriod = bbPeriod; this.bbStd = bbStd;
            this.macdFast = macdFast; this.macdSlow = macdSlow; this.macdSignal = macdSignal; this.atrPeriod = atrPeriod; this.atrSlMult = atrSlMult;
        }
    }
    public static final Map<String, ProfileParams> PROFILES;
    static {Map<String, ProfileParams> m = new HashMap<>();
        m.put("COMMODITY", new ProfileParams(30, 75, 14, 70, 30, 25, 2.5, 12, 30, 9, 14, 2.5));
        m.put("CRYPTO", new ProfileParams(30, 75, 14, 70, 30, 25, 3.5, 12, 30, 9, 14, 2.5));
        m.put("FOREX_IDX", new ProfileParams(40,100, 21, 65, 35, 30, 1.8, 14, 35, 9, 21, 1.5));
        m.put("TECH", new ProfileParams(20, 50, 14, 70, 30, 20, 2.0, 12, 26, 9, 14, 2.0));
        m.put("DEFENSIVE", new ProfileParams(25, 60, 14, 65, 35, 20, 1.8, 12, 26, 9, 14, 1.8));
        PROFILES = Collections.unmodifiableMap(m);
    }
    // Assets
    /** All tradable assets: display-name → ticker (same as Python ASSETS dict). LinkedHashMap preserves insertion order for display purposes.*/
    public static final Map<String, String> ASSETS;
    /** Profile for each asset (same as Python ASSET_PROFILES dict). */
    public static final Map<String, String> ASSET_PROFILES;
    static {LinkedHashMap<String, String> a = new LinkedHashMap<>();
        // Commodities & Futures
        a.put("Gold", "GC=F"); a.put("Silver", "SI=F"); a.put("Oil", "CL=F"); a.put("USDIDX", "DX-Y.NYB"); a.put("Brent_Oil", "BZ=F"); a.put("NATGAS", "HH=F");
        // Forex
        a.put("USD", "UUP"); a.put("EURUSD", "EURUSD=X"); a.put("JPYUSD", "JPYUSD=X"); a.put("GBPUSD", "GBPUSD=X"); a.put("CHFUSD", "CHFUSD=X"); a.put("CZKUSD", "CZKUSD=X");
        // Crypto
        a.put("Bitcoin", "BTC-USD"); a.put("Ethereum", "ETH-USD"); a.put("Solana", "SOL-USD"); a.put("BNB", "BNB-USD"); a.put("XRP", "XRP-USD"); a.put("Cardano", "ADA-USD"); a.put("Avalanche", "AVAX-USD"); a.put("Polkadot", "DOT-USD"); a.put("Chainlink", "LINK-USD"); a.put("Litecoin", "LTC-USD"); a.put("Hype", "HYPE32196-USD"); a.put("Dogecoin", "DOGE-USD");
        // ETF
        a.put("SP500", "SXR8.DE"); a.put("MSCIWorld", "EUNL.DE"); a.put("Nasdaq100", "CNDX.L"); a.put("DAX", "ES1.DE"); a.put("FTSE", "VWCE.DE");
        // Tech
        a.put("Palantir_Technologies", "PLTR"); a.put("Ericsson", "ERIC-B.ST"); a.put("Skoda_Doosan", "DSPW.PR"); a.put("TSM", "TSM"); a.put("MSFT", "MSFT"); a.put("Nokia", "NOKIA.HE"); a.put("GOOGL", "GOOGL"); a.put("Apple", "AAPL"); a.put("Tesla", "TSLA"); a.put("Netflix", "NFLX"); a.put("Colt", "CZG.PR"); a.put("CEZ", "CEZ.PR"); a.put("ORCL", "ORCL"); a.put("NVDA", "NVDA"); a.put("AMD", "AMD"); a.put("Adobe", "ADBE"); a.put("Intel", "INTC"); a.put("Spotify", "SPOT"); a.put("Coinbase", "COIN");
        // Defensive
        a.put("Berkshire_Hathaway", "BRK-A"); a.put("Erste_Group_Bank_PR", "ERBAG.PR"); a.put("Coca-Cola", "KO"); a.put("CocaColaCCH", "CCH.L"); a.put("Altria", "MO"); a.put("Nestle", "NESN.SW"); a.put("AgnicoEagle", "AEM"); a.put("NewmontMining", "NEM"); a.put("NovoNordisk", "NOVO-B.CO"); a.put("Moneta", "MONET.PR"); a.put("KomBanka", "KOMB.PR"); a.put("UBS", "UBSG.SW"); a.put("Zurrich_Insurance", "ZURN.SW"); a.put("Nordea_Bank", "NDA-FI.HE"); a.put("British_American_Tobacco", "BTI"); a.put("Equinor", "EQNR"); a.put("Allianz", "ALV.DE"); a.put("Procter&Gamble", "PG");
        ASSETS = Collections.unmodifiableMap(a);
        // Profile assignments
        Map<String, String> p = new HashMap<>();
        p.put("Gold","COMMODITY"); p.put("Silver","COMMODITY"); p.put("Oil","COMMODITY");
        p.put("NATGAS","COMMODITY"); p.put("Brent_Oil","COMMODITY"); p.put("USDIDX","FOREX_IDX");
        p.put("USD","FOREX_IDX"); p.put("EURUSD","FOREX_IDX"); p.put("JPYUSD","FOREX_IDX");
        p.put("GBPUSD","FOREX_IDX"); p.put("CHFUSD","FOREX_IDX"); p.put("CZKUSD","FOREX_IDX");
        p.put("Bitcoin","CRYPTO"); p.put("Ethereum","CRYPTO"); p.put("Solana","CRYPTO");
        p.put("BNB","CRYPTO"); p.put("XRP","CRYPTO"); p.put("Cardano","CRYPTO");
        p.put("Avalanche","CRYPTO"); p.put("Polkadot","CRYPTO"); p.put("Chainlink","CRYPTO");
        p.put("Litecoin","CRYPTO"); p.put("Hype", "CRYPTO"); p.put("Dogecoin","CRYPTO");
        p.put("SP500","DEFENSIVE"); p.put("MSCIWorld","DEFENSIVE"); p.put("Nasdaq100","DEFENSIVE"); p.put("DAX","DEFENSIVE"); p.put("FTSE","DEFENSIVE");
        p.put("Palantir_Technologies","TECH"); p.put("Ericsson","TECH"); p.put("TSM","TECH"); p.put("Skoda_Doosan","TECH");
        p.put("MSFT","TECH"); p.put("Nokia","TECH");
        p.put("GOOGL","TECH"); p.put("Apple","TECH"); p.put("Tesla","TECH");
        p.put("Netflix","TECH"); p.put("Colt","TECH"); p.put("CEZ","TECH");
        p.put("ORCL","TECH"); p.put("NVDA","TECH"); p.put("AMD","TECH");
        p.put("Adobe","TECH"); p.put("Intel","TECH"); p.put("Spotify","TECH");
        p.put("Coinbase","TECH");
        p.put("Berkshire_Hathaway","DEFENSIVE"); p.put("Erste_Group_Bank_PR","DEFENSIVE");
        p.put("Coca-Cola","DEFENSIVE"); p.put("CocaColaCCH","DEFENSIVE");
        p.put("Altria","DEFENSIVE"); p.put("Nestle","DEFENSIVE");
        p.put("AgnicoEagle","DEFENSIVE"); p.put("NewmontMining","DEFENSIVE");
        p.put("NovoNordisk","DEFENSIVE"); p.put("Moneta","DEFENSIVE");
        p.put("KomBanka","DEFENSIVE"); p.put("UBS","DEFENSIVE");
        p.put("Zurrich_Insurance","DEFENSIVE"); p.put("Nordea_Bank","DEFENSIVE");
        p.put("British_American_Tobacco","DEFENSIVE"); p.put("Equinor","DEFENSIVE");
        p.put("Allianz","DEFENSIVE"); p.put("Procter&Gamble","DEFENSIVE");
        ASSET_PROFILES = Collections.unmodifiableMap(p);
    }
    public static final List<String> ALL_PROFILES = Arrays.asList("TECH","COMMODITY","DEFENSIVE","FOREX_IDX","CRYPTO");// Convenience: all profile keys
}
