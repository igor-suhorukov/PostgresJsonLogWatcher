package com.github.isuhorukov.log.watcher;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.StdErr;
import org.junitpioneer.jupiter.StdIo;
import org.junitpioneer.jupiter.StdOut;
import picocli.CommandLine;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TestCliArgumentParsing {

    /**
     * @plantUml
     */
    @StdIo
    @Test
    @SneakyThrows
    public void testDefaultParameters(StdErr out) {
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            assertEquals(0, postgreSqlJson.saveInterval);
            assertEquals(0, postgreSqlJson.posgreSqlPort);
            assertNull(postgreSqlJson.posgreSqlDatabase);
            assertNull(postgreSqlJson.posgreSqlUserName);
            assertNull(postgreSqlJson.currentLogPositionFile);
            assertEquals(0, postgreSqlJson.maximumQueryCacheSize);
            //init class with default parameters from picocli annotations
            int failed = new CommandLine(postgreSqlJson).execute();
            assertEquals(2, failed);
            assertEquals(10, postgreSqlJson.saveInterval);
            assertEquals(5432, postgreSqlJson.posgreSqlPort);
            assertEquals("postgres", postgreSqlJson.posgreSqlDatabase);
            assertEquals("postgres", postgreSqlJson.posgreSqlUserName);
            assertEquals(".current_log_position", postgreSqlJson.currentLogPositionFile);
            assertEquals(50000, postgreSqlJson.maximumQueryCacheSize);
            assertNull(postgreSqlJson.watchDir);
            assertTrue(out.capturedLines().length > 0);
            assertEquals("Missing required parameter: '<watchDir>'", out.capturedLines()[0]);
        }
    }

    /**
     * @plantUml
     */
    @StdIo
    @Test
    @SneakyThrows
    public void testNonExistingDir(StdOut out) {
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            //init class with default parameters from picocli annotations
            int failed = new CommandLine(postgreSqlJson).execute(UUID.randomUUID().toString());
            assertEquals(1, failed);
            assertTrue(out.capturedLines().length > 0);
            assertTrue(out.capturedLines()[0].contains("PostgreSQL directory"));
            assertTrue(out.capturedLines()[0].contains(postgreSqlJson.watchDir));
            assertTrue(out.capturedLines()[0].contains("with JSON logs not exist"));
        }
    }

    /**
     * @plantUml
     */
    @StdIo
    @Test
    @SneakyThrows
    public void testHelpParameter(StdOut out) {
        System.setProperty("skipProcessExit","true");
        PostgreSqlJson.main(new String[]{"--help"});
        assertTrue(out.capturedLines().length > 0);
        assertEquals("This program reads PostgreSQL DBMS logs in JSON format and sends them to\n" +
                    "OpenTelemetry collector\n" +
                    "Usage: <main class> [-hV] [--password[=<posgreSqlPassword>]]\n" +
                    "                    [-c=<maximumQueryCacheSize>] [-d=<posgreSqlDatabase>]\n" +
                    "                    [-H=<posgreSqlHost>] [-i=<saveInterval>]\n" +
                    "                    [-lp=<currentLogPositionFile>] [-p=<posgreSqlPort>]\n" +
                    "                    [-u=<posgreSqlUserName>] <watchDir>\n" +
                    "      <watchDir>   Path to PostgreSQL log directory in JSON format\n" +
                    "  -c, --max_cache_size=<maximumQueryCacheSize>\n" +
                    "                   Database query cache size\n" +
                    "  -d, --database=<posgreSqlDatabase>\n" +
                    "                   The database name\n" +
                    "  -h, --help       Show this help message and exit.\n" +
                    "  -H, --host=<posgreSqlHost>\n" +
                    "                   The host name of the PostgreSQL server\n" +
                    "  -i, --save_interval=<saveInterval>\n" +
                    "                   Interval of saving (in second) of the current read position\n" +
                    "                     in the log files. The value must be within a range from 1\n" +
                    "                     till 1000 second\n" +
                    "      -lp, --log_pos_file=<currentLogPositionFile>\n" +
                    "                   Path to file to save current processed position in log\n" +
                    "                     files. Required write capability for this program\n" +
                    "  -p, --port=<posgreSqlPort>\n" +
                    "                   The port number the PostgreSQL server is listening on\n" +
                    "      --password[=<posgreSqlPassword>]\n" +
                    "\n" +
                    "  -u, --user=<posgreSqlUserName>\n" +
                    "                   The database user on whose behalf the connection is being\n" +
                    "                     made\n" +
                    "  -V, --version    Print version information and exit.\n", out.capturedString());
        PostgreSqlJson.main(new String[]{"--version"});
        assertTrue(out.capturedLines().length > 0); //because of different behaviour in IDE and Maven tests
    }
}
