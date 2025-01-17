package org.optionsql.strategy;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class ConstraintGenerator {

    public static void main(String[] args) {
        // Validate input arguments
        if (args.length < 3) {
            System.err.println("Usage: java ConstraintGenerator <strategies.json> <ticker.json> <outputdir>");
            System.exit(1);
        }

        String strategiesFilePath = args[0];
        String tickersFilePath = args[1];
        String outputDirPath = args[2];

        try {
            JsonObject strategies = new JsonObject(Files.readString(Path.of(strategiesFilePath)));
            JsonObject tickers = new JsonObject(Files.readString(Path.of(tickersFilePath)));

            // Call reusable method to generate constraints
            generateConstraints(strategies, tickers, outputDirPath);

        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generates constraints for tickers based on strategies and segments.
     *
     * @param strategies    The JSON object containing strategy definitions.
     * @param tickers       The JSON object containing ticker segments.
     * @param outputDirPath The path to the output directory for storing constraints.
     * @throws IOException If an error occurs during file writing.
     */
    public static void generateConstraints(JsonObject strategies, JsonObject tickers, String outputDirPath) throws IOException {
        // Get ticker segments
        JsonObject tickerSegments = tickers.getJsonObject("segments");
        if (tickerSegments == null) {
            throw new IllegalArgumentException("No 'segments' field found in ticker.json");
        }

        // Process each strategy
        for (String strategyName : strategies.fieldNames()) {
            JsonObject strategyConfig = strategies.getJsonObject(strategyName);

            // Extract default constraints
            JsonObject defaultConstraints = Objects.requireNonNull(strategyConfig.getJsonObject("default"),
                    "Strategy " + strategyName + " must have default constraints.");
            JsonObject segments = strategyConfig.getJsonObject("segments");

            // Process each segment in ticker.json
            for (String segmentName : tickerSegments.fieldNames()) {
                JsonArray tickerList = tickerSegments.getJsonArray(segmentName);

                // Use default constraints if the segment is not defined in the strategy
                JsonObject segmentConstraints = (segments != null && segments.containsKey(segmentName))
                        ? segments.getJsonObject(segmentName)
                        : new JsonObject();

                // Generate <ticker>.json for each ticker in this segment
                for (int i = 0; i < tickerList.size(); i++) {
                    String ticker = tickerList.getString(i);

                    // Merge default and segment constraints
                    JsonObject finalConstraints = defaultConstraints.copy().mergeIn(segmentConstraints);

                    // Create output directory structure
                    Path strategyDir = Path.of(outputDirPath, strategyName.toLowerCase().replace(" ", "_"));
                    Files.createDirectories(strategyDir);

                    // Write <ticker>.json file
                    Path outputFile = strategyDir.resolve(ticker.toLowerCase() + ".json");
                    Files.writeString(outputFile, finalConstraints.encodePrettily());
                }
            }
        }

    }
}
