Execute program with OTEL java agent:
```bash
java -Dotel.resource.attributes=service.name=PostgreSQL_Logs -Dotel.exporter.otlp.protocol=grpc -Dotel.exporter.otlp.endpoint=http://127.0.0.1:4317 -Dotel.logs.exporter=otlp -Dotel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes=true -Dotel.instrumentation.logback-appender.experimental.capture-logger-context-attributes=true -javaagent:opentelemetry-javaagent-2.7.0.jar -jar target/PostgresLogParser-1.0-SNAPSHOT.jar ~/database/log
```

Capture interaction:
```bash
mvn clean com.appland:appmap-maven-plugin:prepare-agent package site
```

Convert to plant UML:
```bash
cd tmp/appmap/junit && ~/Downloads/appmap-linux-x64 sequence-diagram --expand package:com/github/isuhorukov/log/watcher -f plantuml *.appmap.json
```

