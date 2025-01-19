package org.optionsql.strategy.csp;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.optionsql.base.BaseService;

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
       startListening();
 //       generateSignals();
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


    private String loadQuery(String queryFilePath) throws Exception {
        String query = new String(Files.readAllBytes(Paths.get(queryFilePath)));
        getLogger().info("Loaded SQL query from: " + queryFilePath);
        return query;
    }

    private JsonObject loadFilterQueries() throws Exception {
        JsonObject sqlConfig = getServiceConfig().getJsonObject("sql").getJsonObject("filter");
        JsonObject filterQueries = new JsonObject();

        // Iterate over each key in the "filter" section
        for (String category : sqlConfig.fieldNames()) {
            JsonArray paths = sqlConfig.getJsonArray(category);
            JsonArray queries = new JsonArray();

            // Load each query file for the current category
            for (int i = 0; i < paths.size(); i++) {
                String path = paths.getString(i);
                String query = loadQuery(path); // Reuse loadQuery

                // Add both the filename and query content
                JsonObject queryObject = new JsonObject()
                        .put("filename", path)
                        .put("query", query);
                queries.add(queryObject);

                getLogger().info("Loaded filter SQL query for category '" + category + "' from: " + path);
            }

            // Add the loaded queries to the JsonObject under their category
            filterQueries.put(category, queries);
        }

        return filterQueries;
    }

    private void generateSignals() {
        try {
            JsonObject sqlConfig = getServiceConfig().getJsonObject("sql");
            String findTickerQuery = loadQuery(sqlConfig.getJsonObject("find").getString("ticker"));
            String findExpirationQuery = loadQuery(sqlConfig.getJsonObject("find").getString("expiration"));
            String findStrikesQuery = loadQuery(sqlConfig.getJsonObject("find").getString("strikes"));
            JsonObject filterQueries = loadFilterQueries();

            ResultSet tickerResultSet = optionsDbConnection.createStatement().executeQuery(findTickerQuery);
            while (tickerResultSet.next()) {
                String ticker = tickerResultSet.getString("ticker_symbol");
                getLogger().info("Processing Ticker: " + ticker);

                String logPrefix = ticker;
                // Apply ticker-level filters
                if (!applyFilters(logPrefix, filterQueries.getJsonArray("ticker"), ticker)) {
                    continue;
                }

                // Execute expiration query
                PreparedStatement expirationStmt = optionsDbConnection.prepareStatement(findExpirationQuery);
                expirationStmt.setString(1, ticker);
                ResultSet expirationResultSet = expirationStmt.executeQuery();
                if (!expirationResultSet.next()) {
                    getLogger().info("No valid expiration found for ticker: " + ticker);
                    continue;
                }
                String expirationDate = expirationResultSet.getString("expiration_date");
                logPrefix = ticker + " | " + expirationDate;
                // Apply expiration-level filters
                if (!applyFilters(logPrefix, filterQueries.getJsonArray("expiration"), ticker, expirationDate)) {
                    continue;
                }

                // Execute strikes query
                PreparedStatement strikesStmt = optionsDbConnection.prepareStatement(findStrikesQuery);
                strikesStmt.setString(1, ticker);
                strikesStmt.setString(2, expirationDate);
                ResultSet strikesResultSet = strikesStmt.executeQuery();

                // Process each strike
                while (strikesResultSet.next()) {
                    double strikePrice = strikesResultSet.getDouble("strike_price");

                    logPrefix = ticker + " | " + expirationDate + " | " + strikePrice;
                    // Apply strike-level filters
                    if (!applyFilters(logPrefix, filterQueries.getJsonArray("strike"), ticker, expirationDate, strikePrice)) {
                        continue;
                    }
                }

                strikesStmt.close();
            }

            tickerResultSet.close();
        } catch (Exception e) {
            e.printStackTrace();
            getLogger().severe("Error during signal generation: " + e.getMessage());
        }
    }

    private boolean applyFilters(String logPrefix, JsonArray filters, Object... params) {
        for (int i = 0; i < filters.size(); i++) {
            JsonObject filter = filters.getJsonObject(i);
            String filename = filter.getString("filename");
            String query = filter.getString("query");
            String ticker = params[0].toString();

            try {
                PreparedStatement stmt = optionsDbConnection.prepareStatement(query);

                // Set parameters dynamically
                for (int j = 0; j < params.length; j++) {
                    stmt.setObject(j + 1, params[j]);
                }

                ResultSet resultSet = stmt.executeQuery();
                boolean passed = resultSet.next() && resultSet.getBoolean(1);

                if (passed) {
                    getLogger().info(logPrefix+": ✅ Filter passed: " + filename);
                } else {
                    getLogger().warning(logPrefix+": ❌ Filter failed: " + filename);
                    return false; // Stop processing on first failed filter
                }
            } catch (Exception e) {
                e.printStackTrace();
                getLogger().severe(logPrefix+": ❌ Error applying filter: " + filename + " | Error: " + e.getMessage());
                return false; // Treat any exception as a failed filter
            }
        }
        return true; // All filters passed
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
