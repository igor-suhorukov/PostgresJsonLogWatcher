/**
 * The {@code com.github.isuhorukov.log.watcher} package provides a command-line tool for reading
 * PostgreSQL DBMS logs in JSON format and sending them to an OpenTelemetry collector. It includes
 * interfaces and implementations for log enrichment, which can optionally add additional data to the logs
 * by fetching SQL statements from a PostgreSQL database using the {@code pg_stat_statements} extension.
 *
 * <p>The main components of this package are:</p>
 *
 * <ul>
 *     <li>{@link com.github.isuhorukov.log.watcher.PostgreSqlJson} - The command-line tool that reads PostgreSQL logs,
 *     enriches them if configured, and sends them to the OpenTelemetry collector.</li>
 *     <li>{@link com.github.isuhorukov.log.watcher.LogEnricher} - An interface for enriching log entries with additional
 *     information from a database.</li>
 *     <li>{@link com.github.isuhorukov.log.watcher.EnrichmentOff} - A no-op implementation of the {@code LogEnricher}
 *     interface that disables log enrichment.</li>
 *     <li>{@link com.github.isuhorukov.log.watcher.LogEnricherPostgreSql} - An implementation of the {@code LogEnricher}
 *     interface that fetches SQL statements from a PostgreSQL database.</li>
 *     <li>{@link com.github.isuhorukov.log.watcher.VersionProvider} - Provides the version information of the project
 *     for the CLI application.</li>
 * </ul>
 *
 * <p>This package is designed to be used as a standalone command-line application, allowing users to monitor and
 * process PostgreSQL logs efficiently. It leverages Java's WatchService to monitor log directories for changes and
 * uses a caching mechanism to optimize database queries.</p>
 *
 * @plantUml
 * title Sequence diagram for PostgresJsonLogWatcher
 * actor User
 * participant "PostgreSqlJson\nCLI Tool" as PostgreSqlJson
 * participant "LogEnricher\nInterface" as LogEnricher
 * participant "EnrichmentOff\nImplementation" as EnrichmentOff
 * participant "LogEnricherPostgreSql\nImplementation" as LogEnricherPostgreSql
 * participant "PostgreSQL\nDatabase" as PostgreSQL
 * participant "OpenTelemetry\nCollector" as OpenTelemetryCollector
 *
 * User -> PostgreSqlJson : Execute Command
 * PostgreSqlJson -> LogEnricher : Initialize Log Enricher
 * alt PostgreSQL Host Configured?
 *     LogEnricher -> LogEnricherPostgreSql : Create Instance
 *     LogEnricherPostgreSql -> PostgreSQL : Connect to Database
 *     LogEnricherPostgreSql -> PostgreSQL : Prepare Statement
 * else PostgreSQL Host Not Configured
 *     LogEnricher -> EnrichmentOff : Create Instance
 * end
 *
 * PostgreSqlJson -> PostgreSQL : Watch Log Directory
 * PostgreSqlJson -> PostgreSQL : Read JSON Log Files
 * PostgreSqlJson -> LogEnricher : Enrich Log Data
 * LogEnricher -> LogEnricherPostgreSql : Get Statement (if configured)
 * LogEnricherPostgreSql -> PostgreSQL : Query pg_stat_statements
 * PostgreSqlJson -> OpenTelemetryCollector : Send Enriched Logs
 */
package com.github.isuhorukov.log.watcher;