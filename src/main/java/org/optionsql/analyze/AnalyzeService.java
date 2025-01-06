package org.optionsql.analyze;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import org.optionsql.base.BaseService;

import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AnalyzeService extends BaseService {

    private String listenAddress;
    private String sqlDir;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private Connection dbConnection;
    private MessageConsumer<Void> consumer;

    public AnalyzeService(String serviceName) {
        super(serviceName);
    }

    @Override
    public void start() throws Exception {
        super.start();

        // Load configuration
        this.listenAddress = getServiceConfig().getString("listen");
        this.sqlDir = getServiceConfig().getString("sqldir");
        var resourcesConfig = getGlobalConfig().getJsonObject("resources").getJsonObject("postgres");
        this.jdbcUrl = "jdbc:postgresql://" + resourcesConfig.getString("hostname") + ":" +
                       resourcesConfig.getInteger("port") + "/" + resourcesConfig.getString("database");
        this.jdbcUser = resourcesConfig.getString("user");
        this.jdbcPassword = resourcesConfig.getString("password");
        openDatabaseConnection();
        startListening();
        getLogger().info("Analyze service started.");
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        closeDatabaseConnection();
    }

    private Future<Void> openDatabaseConnection() {
        Promise<Void> promise = Promise.promise();
        vertx.executeBlocking(blockingPromise -> {
            try {
                dbConnection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
                getLogger().info("Database connection established.");
                blockingPromise.complete();
            } catch (Exception e) {
                blockingPromise.fail(e);
            }
        }).onComplete(res -> {
            if (res.succeeded()) {
                promise.complete();
            } else {
                promise.fail(res.cause());
            }
        });
        return promise.future();
    }

    private void closeDatabaseConnection() {
        if (dbConnection != null) {
            try {
                dbConnection.close();
                getLogger().info("Database connection closed.");
            } catch (Exception e) {
                getLogger().severe("Failed to close database connection: " + e.getMessage());
            }
        }
    }

    private Future<Void> startListening() {
        Promise<Void> promise = Promise.promise();

        consumer = getEventBus().consumer(listenAddress, message -> {
            processSqlFiles()
                    .onSuccess(v -> {
                        getEventBus().publish("analyze.complete", new JsonObject().put("service", "analyze").put("status", "success"));
                        getLogger().info("Analysis completed successfully. Published analyze.complete.");
                    })
                    .onFailure(err -> {
                        getEventBus().publish("analyze.complete", new JsonObject().put("service", "analyze").put("status", "failure").put("payload", err.getMessage()));
                        getLogger().severe("Analysis failed: " + err.getMessage());
                    });
        });

        consumer.completionHandler(res -> {
            if (res.succeeded()) {
                getLogger().info("Listening for messages on address: " + listenAddress);
                promise.complete();
            } else {
                promise.fail(res.cause());
            }
        });

        return promise.future();
    }

    private Future<Void> processSqlFiles() {
        Promise<Void> promise = Promise.promise();
        vertx.executeBlocking(blockingPromise -> {
            try {
                // Fetch SQL files sorted by filename
                List<Path> sqlFiles = Files.list(Paths.get(sqlDir))
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".sql"))
                        .sorted(Comparator.comparing(Path::getFileName))
                        .collect(Collectors.toList());

                // Execute SQL files sequentially
                for (Path sqlFile : sqlFiles) {
                    String sql = Files.readString(sqlFile);
                    try (Statement stmt = dbConnection.createStatement()) {
                        getLogger().info("Executing SQL file: " + sqlFile.getFileName());
                        stmt.execute(sql);
                    }
                }

                blockingPromise.complete();
            } catch (IOException e) {
                blockingPromise.fail("Failed to read SQL files: " + e.getMessage());
            } catch (Exception e) {
                blockingPromise.fail("Failed to execute SQL files: " + e.getMessage());
            }
        }).onComplete(res -> {
            if (res.succeeded()) {
                promise.complete();
            } else {
                promise.fail(res.cause());
            }
        });

        return promise.future();
    }
}
