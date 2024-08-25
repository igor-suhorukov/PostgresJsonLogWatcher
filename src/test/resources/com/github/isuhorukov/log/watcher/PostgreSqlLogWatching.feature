Feature: PostgreSQL Log Watching
  As a developer
  I want to watch PostgreSQL logs
  So that I can verify the application processes logs correctly

Scenario: Watch PostgreSQL logs with Docker container
  Given a temporary directory for Postgres data and log files
  And a PostgreSQL Docker container configured to log in JSON format
  When I start the PostgreSQL container with specific logging configurations
  And logs are generated in the specified directory
  Then the application should detect and process log entries from the PostgreSQL logs
