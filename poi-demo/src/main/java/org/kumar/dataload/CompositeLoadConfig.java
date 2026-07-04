package org.kumar.dataload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.kumar.dataload.util.DataloadBaseClass;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class CompositeLoadConfig {
    public static final String CONFIG_JSON = "composite-load.json";
    public static final String CONFIG_PROPERTY = "composite.load.config";

    public String ledgerFile;
    public String sheetName;
    public String outputFile;
    public String baselineInputDir;
    public String baselineProcessedDir;
    public String accountStatusConfig;
    public String dataloadJson;

    public static CompositeLoadConfig load(String configLocation) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream is = openConfig(configLocation)) {
            CompositeLoadConfig config = mapper.readValue(is, CompositeLoadConfig.class);
            config.validate();
            return config;
        }
    }

    public static CompositeLoadConfig loadDefault() throws IOException {
        String configLocation = System.getProperty(CONFIG_PROPERTY, CONFIG_JSON);
        return load(configLocation);
    }

    private static InputStream openConfig(String configLocation) throws IOException {
        Path configPath = Path.of(configLocation);
        if (Files.exists(configPath)) {
            return Files.newInputStream(configPath);
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream resourceStream = classLoader.getResourceAsStream(configLocation);
        if (resourceStream != null) {
            return resourceStream;
        }

        throw new IOException("Composite load config not found: " + configLocation);
    }

    private void validate() {
        requireNonBlank(ledgerFile, "ledgerFile");
        requireNonBlank(sheetName, "sheetName");
        requireNonBlank(outputFile, "outputFile");
        requireNonBlank(baselineInputDir, "baselineInputDir");
        requireNonBlank(baselineProcessedDir, "baselineProcessedDir");

        if (accountStatusConfig == null || accountStatusConfig.isBlank()) {
            accountStatusConfig = AccountLoadSpecs.ACCOUNT_STATUS_CONFIG_JSON;
        }

        if (dataloadJson == null || dataloadJson.isBlank()) {
            dataloadJson = DataloadBaseClass.DATALOADJSON;
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Composite load config field '" + fieldName + "' is required");
        }
    }
}
