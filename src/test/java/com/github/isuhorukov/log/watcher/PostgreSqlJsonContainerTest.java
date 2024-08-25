package com.github.isuhorukov.log.watcher;

import de.dm.infrastructure.logcapture.LogCapture;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.concurrent.*;

import static de.dm.infrastructure.logcapture.LogExpectation.debug;
import static de.dm.infrastructure.logcapture.LogExpectation.info;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class PostgreSqlJsonContainerTest {

    @RegisterExtension
    public LogCapture logCapture = LogCapture.forCurrentPackage();

    /**
     * Tests the functionality of watching PostgreSQL logs using a Docker container.
     * <p>
     * This test sets up a temporary directory for Postgres data and log files, configures a PostgreSQL Docker container
     * to log in JSON format, and verifies that the application correctly processes these logs.
     * </p>
     *
     * <p>
     * The test uses a {@link PostgreSQLContainer} from the Testcontainers library to run a PostgreSQL instance with
     * specific logging configurations. It evaluates whether the {@link PostgreSqlJson#watchPostgreSqlLogs} method
     * can detect and process log entries in the specified directory.
     * </p>
     *
     * @param tempDir a temporary directory provided by JUnit for test file storage.
     *
     * @plantUml
     */
    @Test
    @SneakyThrows
    public void testWatchPostgreSqlLogsWithContainer(@TempDir Path tempDir) {

        Path pgData = Files.createDirectory(tempDir.resolve("pg_data"));
        log.info("PostgreSQL data directory in test {}",pgData);
        try(PostgreSQLContainer<?> postgresContainer = configurePostgresContainer(pgData)){
            startPostgreSqlContainer(postgresContainer);

            PostgreSqlJson postgreSqlJson = getPostgreSqlJson(postgresContainer, pgData);

            executeSomeTestQueriesInPostgreSql(postgresContainer);

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Future<Integer> resultCode = startLogWatcher(executorService, postgreSqlJson);
            waitEventsAndCloseLogWatcher(postgreSqlJson, resultCode);

            shutdownExecutor(executorService);

            assertExpectedLogEvents();
        }
    }

    public static PostgreSQLContainer<?> configurePostgresContainer(Path pgData) {
        return new PostgreSQLContainer<>("postgres:16")
                .withCommand(
                        "-c", "log_statement=all", "-c", "log_destination=jsonlog", "-c", "logging_collector=on",
                        "-c", "log_statement=all", "-c", "compute_query_id=on", "-c", "log_duration=on",
                        "-c", "shared_preload_libraries=pg_stat_statements",
                        "-c", "track_io_timing=true", "-c", "log_min_duration_statement=0", "-c", "log_checkpoints=on",
                        "-c", "log_connections=on", "-c", "log_disconnections=on", "-c", "log_min_messages=DEBUG5")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withFileSystemBind(pgData.toAbsolutePath().toString(),
                        "/var/lib/postgresql/data", BindMode.READ_WRITE)
                .withInitScript("init_postgres.sql")
                .waitingFor(Wait.forLogMessage(".*Future log output will appear in directory.*", 1));
    }

    public static void shutdownExecutor(ExecutorService executorService) throws InterruptedException {
        executorService.shutdown();
        boolean termination = executorService.awaitTermination(1, TimeUnit.SECONDS);
        assertTrue(termination);
    }

    public static void waitEventsAndCloseLogWatcher(PostgreSqlJson postgreSqlJson, Future<Integer> resultCode) throws InterruptedException, IOException {
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
        postgreSqlJson.getFsWatchService().close();
        try {
            resultCode.get();
            throw new IllegalStateException("Service should throw ClosedWatchServiceException on stop");
        } catch (ExecutionException e) {
            assertEquals("java.nio.file.ClosedWatchServiceException", e.getMessage());
        }
    }

    @NotNull
    public static Future<Integer> startLogWatcher(ExecutorService executorService, PostgreSqlJson postgreSqlJson) {
        Future<Integer> resultCode = executorService.submit(postgreSqlJson::watchPostgreSqlLogs);
        while (postgreSqlJson.getFsWatchService()==null){
            Thread.yield();
        }
        return resultCode;
    }

    public static void startPostgreSqlContainer(PostgreSQLContainer<?> postgresContainer) throws InterruptedException, IOException {
        postgresContainer.start();
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        postgresContainer.execInContainer("chmod", "-R", "777", "/var/lib/postgresql/data");
    }

    @SneakyThrows
    public static PostgreSqlJson getPostgreSqlJson(PostgreSQLContainer<?> postgresContainer, Path pgData) {
        PostgreSqlJson postgreSqlJson = new PostgreSqlJson();
        postgreSqlJson.setPosgreSqlHost(postgresContainer.getHost());
        postgreSqlJson.setPosgreSqlPort(postgresContainer.getFirstMappedPort());
        postgreSqlJson.setPosgreSqlDatabase(postgresContainer.getDatabaseName());
        postgreSqlJson.setPosgreSqlUserName(postgresContainer.getUsername());
        postgreSqlJson.setPosgreSqlPassword(postgresContainer.getPassword());
        postgreSqlJson.setSaveInterval(10);
        Path posFile = Files.createTempFile("test","pos_file");
        postgreSqlJson.setCurrentLogPositionFile(posFile.toString());
        postgreSqlJson.setWatchDir(pgData.resolve("log").toString());
        return postgreSqlJson;
    }

    public static void executeSomeTestQueriesInPostgreSql(PostgreSQLContainer<?> postgresContainer) throws SQLException {
        try (Connection connection = postgresContainer.createConnection("");
             Statement statement = connection.createStatement();
             PreparedStatement preparedStatement = connection.prepareStatement(
                     "SELECT * from generate_series(1, 100) g where g=?")){
            statement.executeQuery("select version()");
            for(int idx=0;idx<10;idx++){
                preparedStatement.setInt(1,idx*5);
                try (ResultSet resultSet = preparedStatement.executeQuery()){
                    while (resultSet.next()){
                        assertEquals(idx*5, resultSet.getInt(1));
                    }
                }
            }
        }
    }

    public void assertExpectedLogEvents() {
        logCapture.assertLogged(info("starting PostgreSQL"));
        logCapture.assertLogged(info("database system is ready to accept connections"));
        logCapture.assertLogged(debug("autovacuum launcher started"));
        logCapture.assertLogged(info("CREATE EXTENSION pg_stat_statements"));
        logCapture.assertLogged(info("execute <unnamed>: select version()"));
        logCapture.assertLogged(info("from generate_series"));
        logCapture.assertLogged(debug("bind <unnamed> to S_1"));
        logCapture.assertLogged(info("ending log output to stderr"));
    }
}
