package com.github.isuhorukov.log.watcher;

import picocli.CommandLine;

import java.io.InputStream;
import java.util.Properties;

/**
 * Provides the version information of the project.
 *
 * <p>This class implements the {@link CommandLine.IVersionProvider} interface to supply
 * version information for the CLI application. The version information is read from
 * a {@code version.properties} file located in the classpath.</p>
 */
public class VersionProvider implements CommandLine.IVersionProvider {

    /**
     * Retrieves the version information from the {@code version.properties} file.
     *
     * @return an array of strings containing the version information.
     * @throws Exception if there is an error reading the version properties file.
     */
    @Override
    public String[] getVersion() throws Exception {
        try (InputStream inputStream = VersionProvider.class.getResourceAsStream("/version.properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            return new String[]{properties.getProperty("project.version")};
        }
    }
}
