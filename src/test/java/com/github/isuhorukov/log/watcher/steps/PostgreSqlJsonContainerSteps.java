package com.github.isuhorukov.log.watcher.steps;

import de.dm.infrastructure.logcapture.LogCapture;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.SneakyThrows;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.isuhorukov.log.watcher.PostgreSqlJsonContainerIT.*;

public class PostgreSqlJsonContainerSteps {
    private Path tempDir;

    private Path pgData;
    private PostgreSQLContainer<?> postgresContainer;
    private final LogCapture logCapture = LogCapture.forPackages("com.github.isuhorukov.log.watcher");


    @Before
    @SneakyThrows
    public void init() {
        tempDir = Files.createTempDirectory("data");
        logCapture.addAppenderAndSetLogLevelToTrace();
    }

    @After
    public void shutdown() {
        postgresContainer.stop();
    }

    @Given("a temporary directory for Postgres data and log files")
    @SneakyThrows
    public void a_temporary_directory_for_postgres_data_and_log_files() {
        pgData = createPgDataDirectory(tempDir);
    }

    @Given("a PostgreSQL Docker container configured to log in JSON format")
    @SneakyThrows
    public void a_postgresql_docker_container_configured_to_log_in_json_format() {
        postgresContainer = configurePostgresContainer(pgData);
    }

    @When("I start the PostgreSQL container with specific logging configurations")
    @SneakyThrows
    public void i_start_the_postgresql_container_with_specific_logging_configurations() {
        startPostgreSqlContainer(postgresContainer);
    }

    @Then("application should detect and process log entries from the PostgreSQL logs")
    @SneakyThrows
    public void the_application_should_detect_and_process_log_entries_from_the_postgresql_logs() {
        applicationProcessLog(postgresContainer, pgData);
    }

    @And("logs are generated in the specified directory and watched & processed by postgres_log_parser")
    @SneakyThrows
    public void logsAreGeneratedInTheSpecifiedDirectory() {
        assertExpectedLogEvents(logCapture);
    }
}
