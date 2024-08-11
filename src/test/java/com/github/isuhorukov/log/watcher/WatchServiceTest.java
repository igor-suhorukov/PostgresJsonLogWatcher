package com.github.isuhorukov.log.watcher;

import de.dm.infrastructure.logcapture.LogCapture;
import de.dm.infrastructure.logcapture.LogExpectation;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;

import static de.dm.infrastructure.logcapture.ExpectedKeyValue.keyValue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WatchServiceTest {
    @Mock
    private WatchService watchService;
    @RegisterExtension
    public LogCapture logCapture = LogCapture.forCurrentPackage();
    private final PostgreSqlJson postgreSqlJson = new PostgreSqlJson(){
        @Override
        protected void positionFileTasks(long saveInterval) throws IOException {
            //do nothing in this test
        }

        @Override
        protected void initialLogImport(File sourceDirectory) throws IOException {
            //do nothin in this test
        }

        @Override
        protected void registerWatchEvent(Path dirToWatch, WatchService watchService) throws IOException {
            //do nothing in this test
        }

        @Override
        protected WatchService getWatchService() throws IOException {
            return watchService;
        }
    };

    @Test
    @SneakyThrows
    void testWatchService(@TempDir Path tempDir) {
        Path jsonLog = Files.createFile(tempDir.resolve("postgresql-2024-08-04_072905.json"));
        Files.write(jsonLog, ("{\"timestamp\":\"2024-08-01 06:55:52.123 UTC\",\"pid\":50," +
                "\"session_id\":\"66ab3178.32\",\"line_num\":4,\"session_start\":\"2024-08-01 06:55:52 UTC\"," +
                "\"txid\":0,\"error_severity\":\"LOG\"," +
                "\"message\":\"database system is ready to accept connections\",\"backend_type\":\"postmaster\"," +
                "\"query_id\":0}").getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
        doNothing().when(watchService).close();
        WatchKey watchKey = mock(WatchKey.class);
        when(watchService.take()).thenReturn(watchKey).thenReturn(null);
        WatchEvent<Path> watchEvent = mock(WatchEvent.class);
        when(watchKey.pollEvents()).thenReturn(Collections.singletonList(watchEvent));
        when(watchEvent.context()).thenReturn(jsonLog.getFileName());
        long saveInterval= 1;
        String watchDir = tempDir.toString();
        postgreSqlJson.setWatchDir(watchDir);
        postgreSqlJson.setSaveInterval(saveInterval);
        postgreSqlJson.watchPostgreSqlLogs();
        logCapture.assertLogged(LogExpectation.info("database system is ready to accept connections",
                keyValue("timestamp", "2024-08-01 06:55:52.123 UTC"), keyValue("pid", 50),
                keyValue("session_id", "66ab3178.32"), keyValue("line_num", 4),
                keyValue("session_start", "2024-08-01 06:55:52 UTC"), keyValue("txid", 0),
                keyValue("backend_type", "postmaster"), keyValue("fileName", "postgresql-2024-08-04_072905.json")));
    }

    @Test
    @SneakyThrows
    void testWatchServiceSkipNonJson(@TempDir Path tempDir) {
        Path jsonLog = Files.createFile(tempDir.resolve("postgresql-2024-08-04_072905.log"));
        Files.write(jsonLog, "".getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
        doNothing().when(watchService).close();
        WatchKey watchKey = mock(WatchKey.class);
        when(watchService.take()).thenReturn(watchKey).thenReturn(null);
        WatchEvent<Path> watchEvent = mock(WatchEvent.class);
        when(watchKey.pollEvents()).thenReturn(Collections.singletonList(watchEvent));
        when(watchEvent.context()).thenReturn(jsonLog.getFileName());
        long saveInterval= 1;
        String watchDir = tempDir.toString();
        postgreSqlJson.setWatchDir(watchDir);
        postgreSqlJson.setSaveInterval(saveInterval);
        postgreSqlJson.watchPostgreSqlLogs();
        logCapture.assertNotLogged(LogExpectation.info());
    }
}
