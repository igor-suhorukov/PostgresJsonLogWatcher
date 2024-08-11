package com.github.isuhorukov.log.watcher;

import de.dm.infrastructure.logcapture.LogCapture;
import de.dm.infrastructure.logcapture.LogExpectation;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static de.dm.infrastructure.logcapture.ExpectedException.exception;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogFilesPositionTest {

    @RegisterExtension
    public LogCapture logCapture = LogCapture.forPackages("cli");
    @Test
    @SneakyThrows
    void savePositionToInvalidPath(@TempDir Path tempDir) {
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()){
            postgreSqlJson.currentLogPositionFile = tempDir.toFile().getAbsolutePath();
            postgreSqlJson.position.put("abc.json",Long.MAX_VALUE);
            postgreSqlJson.saveLogFilesPosition();
            logCapture.assertLogged(LogExpectation.error("Unable to save current log position",
                    exception().expectedMessageRegex("Is a directory").build()));
        }
    }

    @Test
    @SneakyThrows
    void positionFileTasks(@TempDir Path tempDir) {
        Path positionFile = Files.createFile(tempDir.resolve("pos_file"));
        Files.write(positionFile, "{\"postgresql-2024-08-01_065552.json\":136172}".
                getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);

        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()){
            postgreSqlJson.saveInterval = 10;
            postgreSqlJson.currentLogPositionFile = positionFile.toFile().getAbsolutePath();
            Thread thread = postgreSqlJson.positionFileTasks();
            Runtime.getRuntime().removeShutdownHook(thread);
            assertEquals(136172L,postgreSqlJson.position.get("postgresql-2024-08-01_065552.json"));
        }
    }
}
