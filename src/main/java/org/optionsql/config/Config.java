package org.optionsql.config;

import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Config {

    private JsonObject configData;

    public Config(String filePath) throws IOException {
        load(filePath);
    }

    /**
     * Loads the JSON configuration from the specified file path.
     *
     * @param filePath Path to the JSON configuration file.
     * @throws IOException if there is an issue reading the file.
     */
    private void load(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        this.configData = new JsonObject(content);
    }

    /**
     * Gets a specific section of the configuration as a JsonObject.
     *
     * @param sectionKey The key for the section to retrieve.
     * @return The JsonObject corresponding to the section, or null if not found.
     */
    public JsonObject getSection(String sectionKey) {
        return this.configData.getJsonObject(sectionKey);
    }

    /**
     * Gets the entire configuration as a JsonObject.
     *
     * @return The entire configuration.
     */
    public JsonObject getConfigData() {
        return this.configData;
    }
}
