package com.github.isuhorukov.log.watcher;

import de.dm.infrastructure.logcapture.LogCapture;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static de.dm.infrastructure.logcapture.LogExpectation.error;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValidateParameterTest {

    @RegisterExtension
    public LogCapture logCapture = LogCapture.forPackages("cli");

    @Test
    @SneakyThrows
    void missingWatchDir() {
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            assertEquals(1, postgreSqlJson.call());
        }
        logCapture.assertLogged(error("Path to PostgreSQL log directory expected"));
    }

    @Test
    @SneakyThrows
    void nonExistingWatchDir() {
        String watchDir = UUID.randomUUID().toString();
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            postgreSqlJson.setWatchDir(watchDir);
            assertEquals(1, postgreSqlJson.call());
        }
        logCapture.assertLogged(error("PostgreSQL directory '"+watchDir+"' with JSON logs not exist"));
    }

    @Test
    @SneakyThrows
    void nonDirectoryWatchDir(@TempDir Path tempDir) {
        String watchDir = Files.createFile(tempDir.resolve("tempfile.txt")).toAbsolutePath().toString();
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            postgreSqlJson.setWatchDir(watchDir);
            assertEquals(1, postgreSqlJson.call());
        }
        logCapture.assertLogged(error("Path '"+watchDir+"' is not directory"));
    }

    @Test
    @SneakyThrows
    void intervalOutOfRange(@TempDir Path tempDir) {
        String watchDir = tempDir.toAbsolutePath().toString();
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            postgreSqlJson.setWatchDir(watchDir);
            postgreSqlJson.setSaveInterval(0);
            assertEquals(1, postgreSqlJson.call());
            logCapture.assertLogged(error("saveInterval must be between 1 and 1000 sec. Actual value 0"));
            postgreSqlJson.setSaveInterval(Long.MAX_VALUE);
            assertEquals(1, postgreSqlJson.call());
            logCapture.assertLogged(error("saveInterval must be between 1 and 1000 sec. Actual value "+Long.MAX_VALUE));
        }
    }
}
