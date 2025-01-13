package org.optionsql.broker.tws;

import com.ib.client.Bar;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;

import java.util.List;

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

    @Override
    public void onTickSize(int tickerId, int field, int size) {

    }

    @Override
    public void onOptionExpirations(List<String> expirations, List<Double> strikes) {

    }

    @Override
    public void onTickOptionComputation(int tickerId, double impliedVolatility, double delta, double gamma, double vega, double theta, double optPrice, double pvDividend) {

    }

    @Override
    public void onMarketDepth(int tickerId, int position, int operation, int side, double price, int size) {

    }

    @Override
    public void onTickGeneric(int tickerId, int tickType, double value) {

    }

    @Override
    public void onContractDetails(int reqId, ContractDetails contractDetails) {

    }

    @Override
    public void onContractDetailsEnd(int reqId) {

    }
}
