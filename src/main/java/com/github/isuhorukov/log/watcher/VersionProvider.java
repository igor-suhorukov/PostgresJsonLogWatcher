package com.github.isuhorukov.log.watcher;

import picocli.CommandLine;

import java.io.InputStream;
import java.util.Properties;

public class VersionProvider implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() throws Exception {
        try (InputStream inputStream = VersionProvider.class.getResourceAsStream("/version.properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            return new String[]{properties.getProperty("project.version")};
        }
    }
}
