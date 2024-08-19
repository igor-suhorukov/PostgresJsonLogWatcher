Execute program with OTEL java agent:
```bash
java -Dotel.resource.attributes=service.name=PostgreSQL_Logs -Dotel.exporter.otlp.protocol=grpc -Dotel.exporter.otlp.endpoint=http://127.0.0.1:4317 -Dotel.logs.exporter=otlp -Dotel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes=true -Dotel.instrumentation.logback-appender.experimental.capture-logger-context-attributes=true -javaagent:opentelemetry-javaagent-2.7.0.jar -jar postgres_log_parser.jar ~/database/log
```