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
    @StdIo
    @Test
    @SneakyThrows
    void testDefaultParameters(StdErr out) {
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            assertEquals(0, postgreSqlJson.saveInterval);
            assertEquals(0, postgreSqlJson.port);
            assertNull(postgreSqlJson.database);
            assertNull(postgreSqlJson.user);
            assertEquals(0, postgreSqlJson.maximumCacheSize);
            //init class with default parameters from picocli annotations
            int failed = new CommandLine(postgreSqlJson).execute();
            assertEquals(2, failed);
            assertEquals(10, postgreSqlJson.saveInterval);
            assertEquals(5432, postgreSqlJson.port);
            assertEquals("postgres", postgreSqlJson.database);
            assertEquals("postgres", postgreSqlJson.user);
            assertEquals(50000, postgreSqlJson.maximumCacheSize);
            assertNull(postgreSqlJson.watchDir);
            assertTrue(out.capturedLines().length > 0);
            assertEquals("Missing required parameter: '<watchDir>'", out.capturedLines()[0]);
        }
    }
    @StdIo
    @Test
    @SneakyThrows
    void testNonExistingDir(StdOut out) {
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

    @StdIo
    @Test
    @SneakyThrows
    void testHelpParameter(StdOut out) {
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            //init class with default parameters from picocli annotations
            int failed = new CommandLine(postgreSqlJson).execute("--help");
            assertEquals(0, failed);
            assertTrue(out.capturedLines().length > 0);
            assertEquals("This program reads PostgreSQL DBMS logs in JSON format and sends them to\n" +
                    "OpenTelemetry collector\n" +
                    "Usage: <main class> [-hV] [--password[=<password>]] [-c=<maximumCacheSize>]\n" +
                    "                    [-d=<database>] [-H=<host>] [-i=<saveInterval>] [-p=<port>]\n" +
                    "                    [-u=<user>] <watchDir>\n" +
                    "      <watchDir>      Path to PostgreSQL log directory in JSON format\n" +
                    "  -c, --max_cache_size=<maximumCacheSize>\n" +
                    "                      Database query cache size\n" +
                    "  -d, --database=<database>\n" +
                    "                      The database name\n" +
                    "  -h, --help          Show this help message and exit.\n" +
                    "  -H, --host=<host>   The host name of the PostgreSQL server\n" +
                    "  -i, --save_interval=<saveInterval>\n" +
                    "                      Interval of saving (in second) of the current read\n" +
                    "                        position in the log file. The value must be in the\n" +
                    "                        range from 1 to 1000 second\n" +
                    "  -p, --port=<port>   The port number the PostgreSQL server is listening on\n" +
                    "      --password[=<password>]\n" +
                    "\n" +
                    "  -u, --user=<user>   The database user on whose behalf the connection is being\n" +
                    "                        made\n" +
                    "  -V, --version       Print version information and exit.\n", out.capturedString());
        }
    }
}
