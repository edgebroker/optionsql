package org.optionsql.broker.tws;

import com.ib.client.Bar;
import com.ib.client.Contract;

public interface TwsListener {
    // Historical data callbacks
    void onHistoricalData(int reqId, Bar bar);
    void onHistoricalDataEnd(int reqId, String startDate, String endDate);

    // Error handling
    void onError(int id, int errorCode, String errorMsg);

    // Connection lifecycle
    void onConnectionClosed();
    void onConnected();

    // Position callbacks
    void onPositionReceived(String account, Contract contract, double position, double avgCost);
    void onPositionEnd();

    // Market data callbacks
    void onTickPrice(int tickerId, int field, double price);

    // Account update callbacks
    void onAccountUpdate(String key, String value, String currency, String accountName);
}
