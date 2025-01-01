package org.optionsql.store;

import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.optionsql.base.BaseService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class StoreService extends BaseService {

    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private Connection dbConnection;
    private MessageConsumer<JsonObject> dataConsumer; // To manage the event bus listener

    public StoreService() {
        super("store");
    }

    private void startListening() {
        // Subscribe to the event bus address
        String dataAddress = getServiceConfig().getJsonObject("eventbus").getString("data");
        dataConsumer = getEventBus().consumer(dataAddress, message -> {
            JsonObject jsonData = (JsonObject) message.body();
            storeData(jsonData)
                    .onSuccess(v -> getLogger().info("Data successfully stored."))
                    .onFailure(err -> getLogger().severe("Failed to store data: " + err.getMessage()));
        });

        dataConsumer.completionHandler(res -> {
            if (res.succeeded()) {
                getLogger().info("Listening for data on address: " + dataAddress);
            } else {
                getLogger().severe("Failed to start listening for data: " + res.cause().getMessage());
            }
        });
    }
    private Future<Void> openDatabaseConnection() {
        return vertx.executeBlocking(promise -> {
            try {
                dbConnection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
                getLogger().info("Database connection opened.");
                promise.complete();
            } catch (SQLException e) {
                getLogger().severe("Failed to open database connection: " + e.getMessage());
                promise.fail(e);
            }
        });
    }

    private void closeDatabaseConnection() {
        if (dbConnection != null) {
            try {
                dbConnection.close();
                getLogger().info("Database connection closed.");
            } catch (SQLException e) {
                getLogger().severe("Failed to close database connection: " + e.getMessage());
            }
        }
    }

    private Future<Void> preprocessSqlFiles() {
        List<String> sqlFiles = getServiceConfig().getJsonArray("preprocess").getList();
        Future<Void> future = Future.succeededFuture();

        for (String sqlFile : sqlFiles) {
            getLogger().info("Preprocessing SQL file: " + sqlFile);
            future = future.compose(ignored -> processSqlFile(dbConnection, sqlFile));
        }

        return future;
    }

    private Future<Void> storeData(JsonObject data) {
        return vertx.executeBlocking(promise -> {
            try {
                dbConnection.setAutoCommit(false);

                // Store ticker information
                storeTicker(data);

                // Store option chains grouped by expiration
                storeOptionChains(data);

                dbConnection.commit();
                promise.complete();
            } catch (Exception e) {
                try {
                    dbConnection.rollback();
                } catch (SQLException rollbackEx) {
                    getLogger().severe("Rollback failed: " + rollbackEx.getMessage());
                }
                promise.fail(e);
            }
        });
    }
    
    private void storeTicker(JsonObject data) throws SQLException {
        String query = "INSERT INTO ticker (ticker_symbol, current_price, segment) " +
                       "VALUES (?, ?, ?) " +
                       "ON CONFLICT (ticker_symbol) DO UPDATE SET current_price = EXCLUDED.current_price, segment = EXCLUDED.segment";

        try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
            stmt.setString(1, data.getString("ticker_symbol"));
            stmt.setBigDecimal(2, BigDecimal.valueOf(data.getDouble("current_price")));
            stmt.setString(3, data.getString("segment"));
            stmt.executeUpdate();
        }
    }

    private void storeOptionChains(JsonObject data) throws SQLException {
        String query = "INSERT INTO optionchains (ticker_symbol, expiration_date, strike_price, call_bid, call_ask, put_bid, put_ask) " +
                       "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                       "ON CONFLICT (ticker_symbol, expiration_date, strike_price) DO UPDATE SET " +
                       "call_bid = EXCLUDED.call_bid, call_ask = EXCLUDED.call_ask, put_bid = EXCLUDED.put_bid, put_ask = EXCLUDED.put_ask";

        JsonObject options = data.getJsonObject("options");

        for (String expiration : options.fieldNames()) {
            JsonArray strikes = options.getJsonArray(expiration);

            for (int i = 0; i < strikes.size(); i++) {
                JsonObject strike = strikes.getJsonObject(i);

                try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
                    stmt.setString(1, data.getString("ticker_symbol"));
                    stmt.setString(2, expiration);
                    stmt.setBigDecimal(3, BigDecimal.valueOf(strike.getDouble("strike_price")));
                    stmt.setBigDecimal(4, BigDecimal.valueOf(strike.getDouble("call_bid", 0.0)));
                    stmt.setBigDecimal(5, BigDecimal.valueOf(strike.getDouble("call_ask", 0.0)));
                    stmt.setBigDecimal(6, BigDecimal.valueOf(strike.getDouble("put_bid", 0.0)));
                    stmt.setBigDecimal(7, BigDecimal.valueOf(strike.getDouble("put_ask", 0.0)));
                    stmt.executeUpdate();
                }
            }
        }
    }

    @Override
    public void start() throws Exception {
        super.start();

        // Load database configuration
        JsonObject resourcesConfig = getGlobalConfig().getJsonObject("resources").getJsonObject("postgres");
        jdbcUrl = "jdbc:postgresql://" + resourcesConfig.getString("hostname") + ":" + resourcesConfig.getInteger("port") + "/" + resourcesConfig.getString("database");
        jdbcUser = resourcesConfig.getString("user");
        jdbcPassword = resourcesConfig.getString("password");

        getLogger().info("StoreService started but sleeping.");
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }
}
