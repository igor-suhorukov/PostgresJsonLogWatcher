package com.github.isuhorukov.log.watcher;

import de.dm.infrastructure.logcapture.LogCapture;
import lombok.SneakyThrows;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

import static de.dm.infrastructure.logcapture.LogExpectation.error;
import static de.dm.infrastructure.logcapture.LogExpectation.info;
import static org.junit.jupiter.api.Assertions.*;

public class LogEnrichmentTest {

    @RegisterExtension
    public LogCapture logCapture = LogCapture.forPackages("cli");

    @Test
    @SneakyThrows
    void defaultEnrichmentOff() {
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            assertNotNull(postgreSqlJson.logEnricher);
            assertEquals("EnrichmentOff", postgreSqlJson.logEnricher.getClass().getSimpleName());
        }
    }

    @Test
    @SneakyThrows
    void initLogEnricherWoDbHost(){
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            postgreSqlJson.logEnricher = null;
            postgreSqlJson.initLogEnricher();
            assertNotNull(postgreSqlJson.logEnricher);
            assertEquals("EnrichmentOff", postgreSqlJson.logEnricher.getClass().getSimpleName());
        }
    }

    @Test
    @SneakyThrows
    void initLogEnricherWithDbHostWoConnection(){
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            postgreSqlJson.logEnricher = null;
            postgreSqlJson.setHost("127.0.0.1");
            postgreSqlJson.setPort(7432);
            postgreSqlJson.initLogEnricher();
            assertNotNull(postgreSqlJson.logEnricher);
            assertEquals("EnrichmentOff", postgreSqlJson.logEnricher.getClass().getSimpleName());
            logCapture.assertLogged(error("Failed to use log enricher " +
                    LogEnricherPostgreSql.class.getSimpleName()+" for postgres, " +
                    "so I work in mode without log enrichment"));

        }
    }

    @Test
    @SneakyThrows
    void initLogEnricherWithDbHostWoPgStatStatement(){
        Server server = Server.createPgServer("-pgPort", "7432",
                "-ifNotExists","-pgAllowOthers" ,"-key", "postgres", "mem:postgres");
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            server.start();
            postgreSqlJson.logEnricher = null;
            postgreSqlJson.setHost("127.0.0.1");
            postgreSqlJson.setPort(7432);
            postgreSqlJson.setPassword(UUID.randomUUID().toString());
            postgreSqlJson.initLogEnricher();
            assertNotNull(postgreSqlJson.logEnricher);
            assertEquals("EnrichmentOff", postgreSqlJson.logEnricher.getClass().getSimpleName());
            logCapture.assertLogged(error("Make sure the extension is available in the database: " +
                    "CREATE EXTENSION pg_stat_statements;"));
        } finally {
            server.stop();
        }
    }
    @Test
    @SneakyThrows
    void initLogEnricherWithDbHostWithPgStatStatement(){
        String passwordForTest = UUID.randomUUID().toString();

        Server server = Server.createPgServer("-pgPort", "7432",
                "-ifNotExists","-pgAllowOthers" ,"-key", "postgres", "mem:postgres");

        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()) {
            server.start();
            try (Connection connection = DriverManager.getConnection(
                    "jdbc:postgresql://127.0.0.1:7432/postgres?ApplicationName=log_watcher_enricher",
                    "postgres",passwordForTest);
                 Statement statement = connection.createStatement()){

                //emulate pg_stat_statements
                statement.executeUpdate("CREATE TABLE pg_stat_statements(queryid bigint, query text)");

                postgreSqlJson.logEnricher = null;
                postgreSqlJson.setHost("127.0.0.1");
                postgreSqlJson.setPort(7432);
                postgreSqlJson.setPassword(passwordForTest);
                postgreSqlJson.initLogEnricher();
                assertNotNull(postgreSqlJson.logEnricher);
                assertEquals("log_watcher_enricher", postgreSqlJson.logEnricher.enricherApplicationName());
                assertEquals("LogEnricherPostgreSql", postgreSqlJson.logEnricher.getClass().getSimpleName());
                logCapture.assertLogged(info("LogEnricherPostgreSql up and running"));

                //validate logEnricher.getStatement()
                assertNull(postgreSqlJson.logEnricher.getStatement(null));
                assertNull(postgreSqlJson.logEnricher.getStatement(""));
                assertNull(postgreSqlJson.logEnricher.getStatement(" "));
                assertNull(postgreSqlJson.logEnricher.getStatement("abc"));
                assertNull(postgreSqlJson.logEnricher.getStatement("7"));
                statement.executeUpdate(
                        "INSERT INTO pg_stat_statements(queryid,query) VALUES (1024, 'select version()')");
                assertEquals("select version()", postgreSqlJson.logEnricher.getStatement("1024"));
            }
        } finally {
            server.stop();
        }
    }
}
