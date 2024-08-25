Feature: PostgreSQL JSON Log Watching
  As a developer/DevOps
  I want to watch PostgreSQL logs in OpenTelemetry
  So that I run postgres_log_parser and it watch for PostgreSQL JSON logs, parse it and transfer log records with enrichment

Scenario: Watch PostgreSQL logs with Docker container
  Given a temporary directory for Postgres data and log files
  And a PostgreSQL Docker container configured to log in JSON format
  When I start the PostgreSQL container with specific logging configurations
  And application should detect and process log entries from the PostgreSQL logs
  Then logs are generated in the specified directory and watched & processed by postgres_log_parser
