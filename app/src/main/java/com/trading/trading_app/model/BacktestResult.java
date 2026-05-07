package com.trading.trading_app.model;

import java.io.Serializable;
import java.util.List;
/** Result of a historical backtest for one asset. Mirrors run_backtest() output from trading_backtest_script.py.*/
public class BacktestResult implements Serializable {
    public String asset, profile;
    public double initialCapital, finalValue, totalReturn, bhReturn, alpha, winRate, sharpe, maxDrawdown, profitFactor;
    public int totalTrades, winningTrades, losingTrades;
    /** Equity curve: (timestamp_ms, equity_usd) pairs */
    public List<EquityPoint> equityCurve;
    public static class EquityPoint implements Serializable {
        public long timestamp; public double equity;
        public EquityPoint(long timestamp, double equity) { this.timestamp = timestamp; this.equity = equity; }
    }
    /** True if strategy beats buy-and-hold. */
    public boolean beatsHold() { return alpha > 0; }
}
