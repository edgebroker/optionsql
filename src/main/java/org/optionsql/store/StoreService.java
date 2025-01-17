package org.optionsql.store;

import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.optionsql.base.BaseService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;

public class StoreService extends BaseService {

    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private String hostname;
    private int port;
    private String database;
    private String backupDir;
    private Connection dbConnection;
    private MessageConsumer<JsonObject> dataConsumer;

    public StoreService(String serviceName) {
        super(serviceName);
    }

    private void startListening() {
        String dataAddress = getServiceConfig().getString("listen");
        dataConsumer = getEventBus().consumer(dataAddress, message -> {
            JsonObject jsonData = message.body();
            openDatabaseConnection()
                    .compose(v -> backupDatabase())
                    .compose(v -> preprocessSqlFiles())
                    .compose(v -> storeData(jsonData))
                    .onSuccess(v -> {
                        closeDatabaseConnection();
                        sendCompleteToEventBus(true, "");
                        getLogger().info("Data successfully stored.");
                    })
                    .onFailure(err -> {
                        closeDatabaseConnection();
                        sendCompleteToEventBus(false, err.getMessage());
                        getLogger().severe("Failed to store data: " + err.getMessage());
                    });
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
    private Future<Void> backupDatabase() {
        return vertx.executeBlocking(promise -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String backupFile = backupDir + "/backup_" + timestamp + ".sql.gz";

                Path backupDirPath = Paths.get(backupDir);
                if (!Files.exists(backupDirPath)) {
                    Files.createDirectories(backupDirPath);
                }

                String command = String.format(
                        "pg_dump -h %s -p %d -U %s %s | gzip > %s",
                        hostname, port, jdbcUser, database, backupFile);

                ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
                processBuilder.environment().put("PGPASSWORD", jdbcPassword);
                Process process = processBuilder.start();
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    getLogger().info("Database backup and compression completed successfully: " + backupFile);
                    promise.complete();
                } else {
                    throw new IOException("Database backup failed with exit code: " + exitCode);
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to backup and compress database.", e);
                promise.fail(e);
            }
        });
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
                JsonArray options = data.getJsonArray("payload");

                // Store ticker information
                storeTicker(options);

                // Store option chains grouped by expiration
                storeOptionChains(options);

                promise.complete();
            } catch (Exception e) {
                e.printStackTrace();
                promise.fail(e);
            }
        });
    }

    private void storeTicker(JsonArray data) throws SQLException {
        String query = "INSERT INTO ticker (ticker_symbol, current_price, segment, iv_historical_low, iv_historical_high, next_earnings_date, next_earnings_time) " +
                       "VALUES (?, ?, ?, ?, ?, ?, ?) ";

        for (int i = 0; i < data.size(); i++) {
            try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
                JsonObject obj = data.getJsonObject(i);
                stmt.setString(1, obj.getString("ticker_symbol"));
                stmt.setDouble(2, obj.getDouble("current_price"));
                stmt.setString(3, obj.getString("segment"));
                stmt.setDouble(4, obj.getDouble("iv_historical_low"));
                stmt.setDouble(5, obj.getDouble("iv_historical_high"));
                stmt.setString(6, obj.getString("next_earnings_date"));
                stmt.setString(7, obj.getString("next_earnings_time"));
                stmt.executeUpdate();
            }
        }
    }

    private void storeOptionChains(JsonArray options) throws SQLException {
        String query = "INSERT INTO optionchains (" +
                       "ticker_symbol, expiration_date, strike_price, call_bid, call_ask, call_mid, call_volume, call_oi, call_delta, call_gamma, call_theta, call_vega, call_iv, " +
                       "put_bid, put_ask, put_mid, put_volume, put_oi, put_delta, put_gamma, put_theta, put_vega, put_iv" +
                       ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                       " ON CONFLICT (ticker_symbol, expiration_date, strike_price) DO NOTHING";

        try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
            for (int i = 0; i < options.size(); i++) {
                JsonObject tickerData = options.getJsonObject(i);
                String tickerSymbol = tickerData.getString("ticker_symbol");
                JsonObject expirations = tickerData.getJsonObject("expirations");

                // Iterate over each expiration and its strikes
                for (String expiration : expirations.fieldNames()) {
                    JsonArray strikes = expirations.getJsonArray(expiration);

                    for (int j = 0; j < strikes.size(); j++) {
                        JsonObject strikeData = strikes.getJsonObject(j);

                        stmt.setString(1, tickerSymbol);
                        stmt.setString(2, expiration);
                        stmt.setDouble(3, strikeData.getDouble("strike_price", 0.0));

                        // Set call option fields
                        stmt.setDouble(4, strikeData.getDouble("call_bid", 0.0));
                        stmt.setDouble(5, strikeData.getDouble("call_ask", 0.0));
                        stmt.setDouble(6, strikeData.getDouble("call_mid", 0.0));
                        stmt.setInt(7, strikeData.getInteger("call_volume", 0));
                        stmt.setInt(8, strikeData.getInteger("call_oi", 0));
                        stmt.setDouble(9, strikeData.getDouble("call_delta", 0.0));
                        stmt.setDouble(10, strikeData.getDouble("call_gamma", 0.0));
                        stmt.setDouble(11, strikeData.getDouble("call_theta", 0.0));
                        stmt.setDouble(12, strikeData.getDouble("call_vega", 0.0));
                        stmt.setDouble(13, strikeData.getDouble("call_iv", 0.0));

                        // Set put option fields
                        stmt.setDouble(14, strikeData.getDouble("put_bid", 0.0));
                        stmt.setDouble(15, strikeData.getDouble("put_ask", 0.0));
                        stmt.setDouble(16, strikeData.getDouble("put_mid", 0.0));
                        stmt.setInt(17, strikeData.getInteger("put_volume", 0));
                        stmt.setInt(18, strikeData.getInteger("put_oi", 0));
                        stmt.setDouble(19, strikeData.getDouble("put_delta", 0.0));
                        stmt.setDouble(20, strikeData.getDouble("put_gamma", 0.0));
                        stmt.setDouble(21, strikeData.getDouble("put_theta", 0.0));
                        stmt.setDouble(22, strikeData.getDouble("put_vega", 0.0));
                        stmt.setDouble(23, strikeData.getDouble("put_iv", 0.0));

                        stmt.addBatch();
                    }
                }
            }

            stmt.executeBatch();
        }
    }

    private void sendCompleteToEventBus(boolean success, String errorMessage) {
        // Publish to fetch.complete
        JsonObject message = new JsonObject()
                .put("service", "store")
                .put("status", success?"success":"error")
                .put("payload", success?"success":errorMessage);

        getEventBus().publish("store.complete", message);

    }
    @Override
    public void start() throws Exception {
        super.start();
        JsonObject resourcesConfig = getGlobalConfig().getJsonObject("resources").getJsonObject("postgres");
        JsonObject serviceConfig = getServiceConfig();
        hostname = resourcesConfig.getString("hostname");
        port = resourcesConfig.getInteger("port");
        database = serviceConfig.getString("database");
        jdbcUrl = "jdbc:postgresql://" + hostname + ":" + port + "/" + database;
        jdbcUser = resourcesConfig.getString("user");
        jdbcPassword = resourcesConfig.getString("password");
        backupDir = serviceConfig.getString("backupdir");
        startListening();
        getLogger().info("StoreService started");
    }
}
