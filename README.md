# PostgresJsonLogWatcher

The **PostgresJsonLogWatcher** project provides a tool for monitoring PostgreSQL logs in JSON format and sending the logs to an OpenTelemetry collector. This tool can also enrich logs with additional information fetched from the PostgreSQL database.

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)

## Features

- Monitors PostgreSQL logs in JSON format.
- Enriches logs with additional information from the PostgreSQL database.
- Sends enriched logs to an OpenTelemetry collector.
- Configurable via command line arguments.

## Installation

### Prerequisites

- Java 11 or later
- PostgreSQL database with `pg_stat_statements` extension enabled
- OpenTelemetry collector

### Downloading the Release

You can download the latest release from the [GitHub Releases](https://github.com/igor-suhorukov/PostgresJsonLogWatcher/releases) page.

1. Navigate to the [Releases](https://github.com/igor-suhorukov/PostgresJsonLogWatcher/releases) page.
2. Click on the latest release.
3. Download the relevant binary or source code archive for your system.

### Reading GitHub Pages Documentation

Detailed documentation for the project is available on GitHub Pages.

- Navigate to the [Project Documentation](https://igor-suhorukov.github.io/PostgresJsonLogWatcher/) page on GitHub Pages.


### Build

To build the project, run the following Maven command:

```bash
mvn clean install
```
This will generate the executable JAR file in the target directory.

## Usage
To execute the program, run:

```bash
java -jar target/postgres_log_parser.jar -h
```

```
This program reads PostgreSQL DBMS logs in JSON format and sends them to
OpenTelemetry collector
Usage: postgres_log_parser [-hV] [--password[=<posgreSqlPassword>]]
                           [-c=<maximumQueryCacheSize>]
                           [-d=<posgreSqlDatabase>] [-H=<posgreSqlHost>]
                           [-i=<saveInterval>] [-lp=<currentLogPositionFile>]
                           [-p=<posgreSqlPort>] [-u=<posgreSqlUserName>]
                           <watchDir>
      <watchDir>   Path to PostgreSQL log directory in JSON format
  -c, --max_cache_size=<maximumQueryCacheSize>
                   Database query cache size
  -d, --database=<posgreSqlDatabase>
                   The database name
  -h, --help       Show this help message and exit.
  -H, --host=<posgreSqlHost>
                   The host name of the PostgreSQL server
  -i, --save_interval=<saveInterval>
                   Interval of saving (in second) of the current read position
                     in the log files. The value must be within a range from 1
                     till 1000 second
      -lp, --log_pos_file=<currentLogPositionFile>
                   Path to file to save current processed position in log
                     files. Required write capability for this program
  -p, --port=<posgreSqlPort>
                   The port number the PostgreSQL server is listening on
      --password[=<posgreSqlPassword>]

  -u, --user=<posgreSqlUserName>
                   The database user on whose behalf the connection is being
                     made
  -V, --version    Print version information and exit.
```

Execute program with OTEL java agent:
```bash
java -Dotel.resource.attributes=service.name=PostgreSQL_Logs -Dotel.exporter.otlp.protocol=grpc -Dotel.exporter.otlp.endpoint=http://127.0.0.1:4317 -Dotel.logs.exporter=otlp -Dotel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes=true -Dotel.instrumentation.logback-appender.experimental.capture-logger-context-attributes=true -javaagent:opentelemetry-javaagent-2.7.0.jar -jar postgres_log_parser.jar ~/database/log
```