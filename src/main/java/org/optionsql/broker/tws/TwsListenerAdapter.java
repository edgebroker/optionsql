package org.optionsql.broker.tws;

import com.ib.client.Bar;
import com.ib.client.Contract;

public class TwsListenerAdapter implements TwsListener {
    @Override
    public void onHistoricalData(int reqId, Bar bar) {

    }

    @Override
    public void onHistoricalDataEnd(int reqId, String startDate, String endDate) {

    }

    @Override
    public void onError(int id, int errorCode, String errorMsg) {

    }

    @Override
    public void onConnectionClosed() {

    }

    @Override
    public void onConnected() {

    }

    @Override
    public void onPositionReceived(String account, Contract contract, double position, double avgCost) {

    }

    @Override
    public void onPositionEnd() {

    }

    @Override
    public void onTickPrice(int tickerId, int field, double price) {

    }

    @Override
    public void onAccountUpdate(String key, String value, String currency, String accountName) {

    }
}
