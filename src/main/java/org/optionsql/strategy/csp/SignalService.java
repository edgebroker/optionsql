package org.optionsql.strategy.csp;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.optionsql.base.BaseService;
import org.optionsql.strategy.util.SqlUtil;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SignalService extends BaseService {

    private Connection optionsDbConnection;
    private Connection strategyDbConnection;
    private String signalQuery;
    private String[] filterQueries;

    public SignalService(String serviceName) {
        super(serviceName);
    }

    @Override
    public void start() throws Exception {
        super.start();

        // Load database configurations
        JsonObject dbConfig = getGlobalConfig().getJsonObject("resources").getJsonObject("postgres");
        JsonObject serviceConfig = getServiceConfig();
        JsonObject databases = serviceConfig.getJsonObject("databases");

        // Connect to the options database
        String optionsDbName = databases.getString("options");
        optionsDbConnection = createDatabaseConnection(dbConfig, optionsDbName);
        getLogger().info("Connected to Options database: " + optionsDbName);

        // Connect to the strategy database
        String strategyDbName = databases.getString("strategy");
        strategyDbConnection = createDatabaseConnection(dbConfig, strategyDbName);
        getLogger().info("Connected to Strategy database: " + strategyDbName);

        // Start listening for events
//        startListening();
        generateSignals();
    }

    private Connection createDatabaseConnection(JsonObject dbConfig, String dbName) throws SQLException {
        String jdbcUrl = "jdbc:postgresql://" + dbConfig.getString("hostname") + ":" + dbConfig.getInteger("port") + "/" + dbName;
        String user = dbConfig.getString("user");
        String password = dbConfig.getString("password");
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    private void startListening() {
        String listenAddress = getServiceConfig().getString("listen");
        getEventBus().consumer(listenAddress, message -> {
            getLogger().info("Received completion event on: " + listenAddress);
            generateSignals();
        });
        getLogger().info("Listening for events on: " + listenAddress);
    }


    private void loadSignalQuery() throws Exception {
        String queryFilePath = getServiceConfig().getJsonObject("sql").getString("signal");
        signalQuery = new String(Files.readAllBytes(Paths.get(queryFilePath)));
        getLogger().info("Loaded signal generation SQL query from: " + queryFilePath);
    }

    private void loadFilterQueries() throws Exception {
        JsonArray filterPaths = getServiceConfig().getJsonObject("sql").getJsonArray("filter");
        filterQueries = new String[filterPaths.size()];
        for (int i = 0; i < filterPaths.size(); i++) {
            filterQueries[i] = new String(Files.readAllBytes(Paths.get(filterPaths.getString(i))));
            getLogger().info("Loaded filter SQL query from: " + filterPaths.getString(i));
        }
    }

    private void generateSignals() {
        try {
            loadSignalQuery();  // Reload the query to reflect changes
            loadFilterQueries();

            ResultSet tickerResultSet = optionsDbConnection.createStatement().executeQuery("SELECT ticker_symbol FROM ticker");
            while (tickerResultSet.next()) {
                String ticker = tickerResultSet.getString("ticker_symbol");
                String expirationDate = SqlUtil.findClosestExpiration(optionsDbConnection, ticker, 45);
                getLogger().info("Processing Ticker: " + ticker + " with Expiration: " + expirationDate);

                try (PreparedStatement stmt = optionsDbConnection.prepareStatement(signalQuery)) {
                    stmt.setString(1, ticker);
                    stmt.setString(2, expirationDate);

                    ResultSet rs = stmt.executeQuery();
                    JsonArray signalsArray = new JsonArray();

                    while (rs.next()) {
                        String signalsJson = rs.getString("signals");
                        if (signalsJson != null && !signalsJson.isEmpty()) {
                            JsonArray signals = new JsonArray(signalsJson);
                            for (int i = 0; i < signals.size(); i++) {
                                JsonObject signal = signals.getJsonObject(i);
                                double strikePrice = signal.getDouble("strike_price");
                                if (applyAllFilters(ticker, expirationDate, strikePrice)) {
                                    signalsArray.add(signal);
                                }
                            }
                        }   else {
                        getLogger().info("No signals generated for ticker: " + ticker + " and expiration: " + expirationDate);
                    }

                }

                    getLogger().info("Generated Signals for " + ticker + ": " + signalsArray.encodePrettily());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            getLogger().severe("Error during signal generation: " + e.getMessage());
        }
    }

    private boolean applyAllFilters(String ticker, String expirationDate, double strikePrice) {
        try {
            for (int i = 0; i < filterQueries.length; i++) {
                String filterQuery = filterQueries[i];
                String filterFileName = getServiceConfig().getJsonObject("sql").getJsonArray("filter").getString(i);
                if (!applyFilter(filterQuery, filterFileName, ticker, expirationDate, strikePrice)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            getLogger().severe("Error applying filters: " + e.getMessage());
            return false;
        }
    }

    private boolean applyFilter(String sqlQuery, String filterFileName, String ticker, String expirationDate, double strikePrice) throws Exception {
        try (PreparedStatement stmt = optionsDbConnection.prepareStatement(sqlQuery)) {
            stmt.setString(1, ticker);
            stmt.setString(2, expirationDate);
            stmt.setDouble(3, strikePrice);

            ResultSet rs = stmt.executeQuery();
             if (rs.next() && rs.getBoolean("passed")) {
                getLogger().info("✅ Passed filter [" + filterFileName + "] for Ticker: " + ticker + ", Expiration: " + expirationDate + ", Strike: " + strikePrice);
                return true;
            } else {
                getLogger().info("❌ Failed filter [" + filterFileName + "] for Ticker: " + ticker + ", Expiration: " + expirationDate + ", Strike: " + strikePrice);
                return false;
            }
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (optionsDbConnection != null && !optionsDbConnection.isClosed()) {
            optionsDbConnection.close();
            getLogger().info("Options database connection closed.");
        }
        if (strategyDbConnection != null && !strategyDbConnection.isClosed()) {
            strategyDbConnection.close();
            getLogger().info("Strategy database connection closed.");
        }
    }
}
