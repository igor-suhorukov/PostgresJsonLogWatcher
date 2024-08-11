package com.github.isuhorukov.log.watcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

@CommandLine.Command(mixinStandardHelpOptions = true, version = "1.0.0",
        header = "This program reads PostgreSQL DBMS logs in JSON format and sends them to OpenTelemetry collector")
@Setter
public class PostgreSqlJson implements Callable<Integer>, Closeable {

    public static final String DURATION = "duration: ";
    public static final String MS = " ms";
    public static final String PLAN = "plan:\n";
    public static final String QUERY_ID = "query_id";
    public static final String JSON_SUFFIX = ".json";

    private static final Logger logger = LoggerFactory.getLogger(PostgreSqlJson.class);
    private static final Logger cliLogger = LoggerFactory.getLogger("cli");

    private static final ObjectMapper mapper = new ObjectMapper();

    final Map<String, Long> position = new ConcurrentHashMap<>();

    LogEnricher logEnricher = new EnrichmentOff();

    @CommandLine.Parameters(index = "0", description = "Path to PostgreSQL log directory in JSON format")
    String watchDir;
    @CommandLine.Option(names = {"-i", "--save_interval"},  defaultValue = "10",
            description = "Interval of saving (in second) of the current read position in the log files. " +
                    "The value must be within a range from 1 till 1000 second")
    long saveInterval;
    @CommandLine.Option(names = {"-H", "--host"}, description = "The host name of the PostgreSQL server")
    String posgreSqlHost;
    @CommandLine.Option(names = {"-p", "--port"}, defaultValue = "5432", description = "The port number the PostgreSQL server is listening on")
    int posgreSqlPort;
    @CommandLine.Option(names = {"-d", "--database"}, defaultValue = "postgres", description = "The database name")
    String posgreSqlDatabase;
    @CommandLine.Option(names = {"-u", "--user"}, defaultValue = "postgres",
            description = "The database user on whose behalf the connection is being made")
    String posgreSqlUserName;

    @CommandLine.Option(names = "--password", arity = "0..1", interactive = true)
    String posgreSqlPassword = System.getenv("PGPASSWORD");

    @CommandLine.Option(names = {"-c", "--max_cache_size"},  defaultValue = "50000",
            description = "Database query cache size")
    int maximumQueryCacheSize;
    @CommandLine.Option(names = {"-lp", "--log_pos_file"}, defaultValue = ".current_log_position",
            description = "Path to file to save current processed position in log files. " +
                    "Required write capability for this program")
    String currentLogPositionFile;

    @SneakyThrows
    public static void main(String[] args) {
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()){
            System.exit(new CommandLine(postgreSqlJson).execute(args));
        }
    }

    @Override
    public Integer call() throws Exception {
        return watchPostgreSqlLogs();
    }

    public int watchPostgreSqlLogs() throws IOException, InterruptedException {
        if(watchDir==null || watchDir.trim().isEmpty()){
            cliLogger.error("Path to PostgreSQL log directory expected");
            return 1;
        }
        File sourceDirectory = new File(watchDir);
        if(!sourceDirectory.exists()) {
            cliLogger.error("PostgreSQL directory '{}' with JSON logs not exist", watchDir);
            return 1;
        }
        if(!sourceDirectory.isDirectory()){
            cliLogger.error("Path '{}' is not directory", watchDir);
            return 1;
        }
        if(saveInterval<=0 || saveInterval>1000){
            cliLogger.error("saveInterval must be between 1 and 1000 sec. Actual value {}", saveInterval);
            return 1;
        }
        initLogEnricher();
        positionFileTasks(saveInterval);
        initialLogImport(sourceDirectory);
        Path dirToWatch = Paths.get(watchDir);
        try (WatchService watchService = getWatchService()) {
            registerWatchEvent(dirToWatch, watchService);
            WatchKey key;
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    String fileName = event.context().toString();
                    if(!fileName.endsWith(JSON_SUFFIX)){
                        continue;
                    }
                    readJsonLog(new File(watchDir, fileName));
                }
                key.reset();
            }
        }
        return 0;
    }

    void initLogEnricher() {
        if(posgreSqlHost !=null && !posgreSqlHost.isEmpty()){
            try {
                logEnricher = new LogEnricherPostgreSql(posgreSqlHost, posgreSqlPort, posgreSqlDatabase, posgreSqlUserName, posgreSqlPassword, maximumQueryCacheSize);
            } catch (Exception e) {
                cliLogger.error("Failed to use log enricher {} for postgres, so I work in mode without log enrichment",
                        LogEnricherPostgreSql.class.getSimpleName(), e);
                logEnricher = new EnrichmentOff();
            }
            try {
                logEnricher.getStatement("0");
                cliLogger.info("{} up and running", LogEnricherPostgreSql.class.getSimpleName());
            } catch (Exception e) {
                cliLogger.error("Make sure the extension is available in the database: CREATE EXTENSION pg_stat_statements;\n" +
                        "https://www.postgresql.org/docs/current/pgstatstatements.html", e);
                logEnricher = new EnrichmentOff();
            }

        } else {
            logEnricher =  new EnrichmentOff();
        }
    }

    protected void registerWatchEvent(Path dirToWatch, WatchService watchService) throws IOException {
        dirToWatch.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
    }

    protected WatchService getWatchService() throws IOException {
        return FileSystems.getDefault().newWatchService();
    }

    protected void initialLogImport(File sourceDirectory) throws IOException {
        File[] jsonLogs = sourceDirectory.listFiles(pathname -> pathname.getName().endsWith(JSON_SUFFIX));

        if(jsonLogs!=null && jsonLogs.length>0){
            Arrays.sort(jsonLogs, Comparator.comparing(File::getName));
            for(File jsonLog: jsonLogs){
                readJsonLog(jsonLog);
            }
        }
    }

    @SneakyThrows
    void parseLogLine(String line, String logName){
        JsonNode jsonNode = mapper.readTree(line);
        Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();

        String message = jsonNode.at("/message").asText();
        if(logEnricher.enricherApplicationName()!=null && message.contains(logEnricher.enricherApplicationName())){
            return;
        }
        LoggingEventBuilder loggingEventBuilder = logger.atLevel(getSeverity(jsonNode.at("/error_severity").asText()));
        if(message.startsWith(DURATION)){
            int msEndIndex = message.indexOf(MS);
            double duration = Double.parseDouble(message.substring(DURATION.length(), msEndIndex));
            loggingEventBuilder.addKeyValue("duration", duration);
            int planIdx = message.indexOf(PLAN);
            if(planIdx!=-1){
                String plan = message.substring(planIdx + PLAN.length());
                loggingEventBuilder.addKeyValue("plan", plan);
            } else
            if(message.indexOf(" parse ",msEndIndex+2)>-1){
                loggingEventBuilder.addKeyValue("parse", true);
            } else
            if(message.indexOf(" bind ",msEndIndex+2)>-1){
                loggingEventBuilder.addKeyValue("bind", true);
            }
            loggingEventBuilder.setMessage("");
        } else {
            loggingEventBuilder.setMessage(message);
            if(message.startsWith("statement: ")){
                loggingEventBuilder.addKeyValue("statement", true);
            } else
            if(message.startsWith("execute")){
                loggingEventBuilder.addKeyValue("execute", true);
            }
        }
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            String value = entry.getValue().asText();
            if("error_severity".equals(key) && (value.equals("INFO") || value.equals("ERROR")
                    || value.equals("DEBUG") || value.equals("TRACE")) ||
                    "message".equals(key) ||
                    (QUERY_ID.equals(key) && "0".equals(value)) //skip empty query_id
            ){
                continue;
            }
            if("application_name".equals(key) && value.equals(logEnricher.enricherApplicationName())){
                return; //skip enricher log record by clientName
            }
            if(QUERY_ID.equals(key)){
                String queryId = value;
                String statement = logEnricher.getStatement(queryId);
                if(statement!=null && !statement.isEmpty()){
                    loggingEventBuilder.addKeyValue("statement_text", statement);
                }
            }
            JsonNode entryValue = entry.getValue();
            loggingEventBuilder.addKeyValue(key, getValue(entryValue));
        }
        loggingEventBuilder.addKeyValue("fileName", logName);
        loggingEventBuilder.log();
    }

    private static Object getValue(JsonNode entryValue) {
        Object value;
        switch (entryValue.getNodeType()){
            case BOOLEAN:
                value = entryValue.booleanValue();
                break;
            case NUMBER:
                switch (entryValue.numberType()){
                    case INT:
                        value = entryValue.asInt();
                        break;
                    case LONG:
                        value = entryValue.asLong();
                        break;
                    default:
                        value = entryValue.asText();
                        break;
                }
                break;
            default:
                value = entryValue.asText();
                break;
        }
        return value;
    }

    private static Level getSeverity(String severity) {
        switch (severity){
            case "LOG": return Level.INFO;
            case "ERROR": return Level.ERROR;
            case "DEBUG": return Level.DEBUG;
            case "TRACE": return Level.TRACE;
            case "FATAL": return Level.ERROR;
            default:
                throw new IllegalArgumentException(severity);
        }
    }
    protected void positionFileTasks(long saveInterval) throws IOException {
        if(new File(currentLogPositionFile).exists()) {
            position.putAll(mapper.readValue(new File(currentLogPositionFile),
                    new TypeReference<ConcurrentHashMap<String, Long>>() {}));
        }
        new Timer("LogPositionSaver", true).schedule(new TimerTask() {
            @Override
            public void run() {
                saveLogFilesPosition();
            }
        }, TimeUnit.SECONDS.toMillis(saveInterval), TimeUnit.SECONDS.toMillis(saveInterval));
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveLogFilesPosition));
    }

    protected synchronized void saveLogFilesPosition() {
        try {
            if(position.isEmpty()){
                return;
            }
            try (FileOutputStream currentPostitionFile = new FileOutputStream(currentLogPositionFile)){
                mapper.writeValue(currentPostitionFile,new TreeMap<>(position));
            }
        } catch (Exception e) {
            cliLogger.error("Unable to save current log position", e);
        }
    }

    void readJsonLog(File jsonLog) throws IOException {
        String jsonLogName = jsonLog.getName();
        long from = position.computeIfAbsent(jsonLogName, name -> 0L);
        if(jsonLog.length() ==0){
            return;
        }
        if(jsonLog.length()==0 || jsonLog.length()<=position.computeIfAbsent(jsonLogName, name -> 0L)){
            return;
        }
        try (RandomAccessFile randomAccessJson = new RandomAccessFile(jsonLog, "r")) {
            randomAccessJson.seek(from);
            String line;
            while ((line = randomAccessJson.readLine())!= null) {
                parseLogLine(line, jsonLogName);
            }
        }
        position.put(jsonLogName, jsonLog.length());
    }

    @Override
    public void close() throws IOException {
        logEnricher.close();
    }
}
