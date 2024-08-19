command line parameters:
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