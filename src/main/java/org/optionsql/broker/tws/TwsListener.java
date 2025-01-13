package org.optionsql.broker.tws;

import com.ib.client.Bar;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;

import java.util.List;

public interface TwsListener {
    // --- Historical Data Callbacks ---
    void onHistoricalData(int reqId, Bar bar);
    void onHistoricalDataEnd(int reqId, String startDate, String endDate);

    // --- Error Handling ---
    void onError(int id, int errorCode, String errorMsg);

    // --- Connection Lifecycle ---
    void onConnectionClosed();
    void onConnected();

    // --- Position Callbacks ---
    void onPositionReceived(String account, Contract contract, double position, double avgCost);
    void onPositionEnd();

    // --- Market Data Callbacks ---
    void onTickPrice(int tickerId, int field, double price);
    void onTickSize(int tickerId, int field, int size);  // Added for volume and OI

    // --- Account Update Callbacks ---
    void onAccountUpdate(String key, String value, String currency, String accountName);

    // --- Option Chain Specific Callbacks ---
    void onOptionExpirations(List<String> expirations, List<Double> strikes);         // Expiration dates for a symbol
    void onTickOptionComputation(
            int tickerId,
            double impliedVolatility,
            double delta,
            double gamma,
            double vega,
            double theta,
            double optPrice,
            double pvDividend
    );  // Greeks & IV data
    void onContractDetails(int reqId, ContractDetails contractDetails);
    void onContractDetailsEnd(int reqId);

    // --- Market Depth (optional) ---
    void onMarketDepth(int tickerId, int position, int operation, int side, double price, int size);

    // --- Generic Tick Data ---
    void onTickGeneric(int tickerId, int tickType, double value);
}
