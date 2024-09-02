package com.github.isuhorukov.log.watcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
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

/**
 * The {@code PostgreSqlJson} class is a command-line tool for reading
 * PostgreSQL DBMS logs in JSON format and sending them to an OpenTelemetry collector.
 * It can optionally use a {@link LogEnricher} to add additional data to the logs.
 *
 * @plantUml
 * database "PostgreSQL 15+"
 * node postgres_log_parser #palegreen
 * node "OpenTelemetry collector"
 * postgres_log_parser - "PostgreSQL 15+" : watch changes and parse JSON logs
 * postgres_log_parser -(0- "OpenTelemetry collector": sending the logs
 */
@CommandLine.Command(mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
        name = "postgres_log_parser",
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
    @Getter
    private WatchService fsWatchService;

    @SneakyThrows
    public static void main(String[] args) {
        try (PostgreSqlJson postgreSqlJson = new PostgreSqlJson()){
            int exitCode = new CommandLine(postgreSqlJson).execute(args);
            if(!Boolean.getBoolean("skipProcessExit")){
                System.exit(exitCode);
            }
        }
    }

    @Override
    public Integer call() throws Exception {
        return watchPostgreSqlLogs();
    }

    /**
     * Monitors the PostgreSQL log directory for changes and processes the logs.
     * <p>
     * This method sets up a WatchService to continuously monitor the specified
     * directory for new or modified log files in JSON format. When such a file
     * is detected, it is processed to extract relevant log information.
     * </p>
     *
     * <p>
     * The method also makes use of a log enricher (if configured) to enhance the log data
     * with additional information.
     * </p>
     *
     * @throws IOException if an I/O error occurs initializing the watcher or processing the logs.
     * @throws InterruptedException if the watch service is interrupted while waiting for events.
     */
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
        positionFileTasks();
        initialLogImport(sourceDirectory);
        Path dirToWatch = Paths.get(watchDir);
        fsWatchService = getWatchService();
        try (WatchService watchService = fsWatchService) {
            registerWatchEvent(dirToWatch, watchService);
            WatchKey key;
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    String fileName = event.context().toString();
                    if(!fileName.endsWith(JSON_SUFFIX)){
                        continue;
                    }
                    readJsonLog(new File(watchDir, fileName));
                    Thread.yield();
                }
                key.reset();
            }
        }
        return 0;
    }

    /**
     * Initializes the log enricher for PostgreSQL logs.
     * <p>
     * This method creates an instance of {@link LogEnricherPostgreSql} with the specified
     * PostgreSQL host, port, database, username, password, and query cache size.
     * </p>
     * <p>
     * If the PostgreSQL host is not set or is empty, the method does not initialize the
     * log enricher, logs an error and instantiate EnrichmentOff instead of LogEnricherPostgreSql.
     * </p>
     */
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

    /**
     * Performs the initial import of PostgreSQL JSON log files from the specified directory.
     * <p>
     * This method reads all JSON log files in the given directory, sorts them by name,
     * and processes each file by calling the {@link #readJsonLog(File)} method.
     * </p>
     *
     * @param sourceDirectory the directory containing the PostgreSQL JSON log files to be imported.
     * @throws IOException if an I/O error occurs while reading the log files.
     */
    protected void initialLogImport(File sourceDirectory) throws IOException {
        File[] jsonLogs = sourceDirectory.listFiles(pathname -> pathname.getName().endsWith(JSON_SUFFIX));

        if(jsonLogs!=null && jsonLogs.length>0){
            Arrays.sort(jsonLogs, Comparator.comparing(File::getName));
            for(File jsonLog: jsonLogs){
                readJsonLog(jsonLog);
            }
        }
    }

    /**
     * Parses a single line of PostgreSQL JSON log and logs it according to its severity level.
     * <p>
     * This method reads a log line in JSON format, determines the severity level of the log message,
     * and logs the message using the appropriate logging level. If the corresponding logging level
     * (TRACE, DEBUG, INFO, WARN, ERROR) is not enabled, the method will return immediately without
     * further processing the log line.
     * </p>
     * <p>
     * The method uses the {@code error_severity} field from the JSON log to determine the logging level
     * and creates a logging event with additional key-value pairs extracted from the JSON log.
     * It also handles various log message formats, adjusting or adding specific log attributes such as
     * duration, plan, parse, and bind if they are present in the message.
     * </p>
     * @param line the log line in JSON format to be parsed.
     * @param logName the name of the log file from which the line was read.
     */    @SneakyThrows
    void parseLogLine(String line, String logName){
        JsonNode jsonNode = mapper.readTree(line);
        Level severity = getSeverity(jsonNode.at("/error_severity").asText());
        switch (severity){
            case TRACE:
                if(!logger.isTraceEnabled()) return;
                break;
            case DEBUG:
                if(!logger.isDebugEnabled()) return;
                break;
            case INFO:
                if(!logger.isInfoEnabled()) return;
                break;
            case WARN:
                if(!logger.isWarnEnabled()) return;
                break;
            case ERROR:
                if(!logger.isErrorEnabled()) return;
                break;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();

        String message = jsonNode.at("/message").asText();
        if(logEnricher.enricherApplicationName()!=null && message.contains(logEnricher.enricherApplicationName())){
            return;
        }

        LoggingEventBuilder loggingEventBuilder = logger.atLevel(severity);
        if(message.startsWith(DURATION)){
            int msEndIndex = message.indexOf(MS);
            double duration = Double.parseDouble(message.substring(DURATION.length(), msEndIndex));
            loggingEventBuilder = loggingEventBuilder.addKeyValue("duration", duration);
            int planIdx = message.indexOf(PLAN);
            if(planIdx!=-1){
                String plan = message.substring(planIdx + PLAN.length());
                loggingEventBuilder = loggingEventBuilder.addKeyValue("plan", plan);
            } else
            if(message.indexOf(" parse ",msEndIndex+2)>-1){
                loggingEventBuilder = loggingEventBuilder.addKeyValue("parse", true);
            } else
            if(message.indexOf(" bind ",msEndIndex+2)>-1){
                loggingEventBuilder = loggingEventBuilder.addKeyValue("bind", true);
            }
            loggingEventBuilder = loggingEventBuilder.setMessage("");
        } else {
            loggingEventBuilder = loggingEventBuilder.setMessage(message);
            if(message.startsWith("statement: ")){
                loggingEventBuilder = loggingEventBuilder.addKeyValue("statement", true);
            } else
            if(message.startsWith("execute")){
                loggingEventBuilder = loggingEventBuilder.addKeyValue("execute", true);
            }
        }
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            String value = entry.getValue().asText();
            if("message".equals(key) ||
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
                    loggingEventBuilder = loggingEventBuilder.addKeyValue("statement_text", statement);
                }
            }
            JsonNode entryValue = entry.getValue();
            loggingEventBuilder = loggingEventBuilder.addKeyValue(key, getValue(entryValue));
        }
        loggingEventBuilder = loggingEventBuilder.addKeyValue("fileName", logName);
        loggingEventBuilder.log();
    }

    private static Object getValue(JsonNode entryValue) {
        switch (entryValue.getNodeType()){
            case NUMBER:
                switch (entryValue.numberType()){
                    case INT:
                        return entryValue.asInt();
                    case LONG:
                        return entryValue.asLong();
                }
            case STRING:
            default:
                return entryValue.asText();
        }
    }

    private static Level getSeverity(String severity) {
        //https://www.postgresql.org/docs/current/runtime-config-logging.html#RUNTIME-CONFIG-SEVERITY-LEVELS
        switch (severity){
            case "DEBUG":
            case "DEBUG5":
            case "DEBUG4":
            case "DEBUG3":
            case "DEBUG2":
            case "DEBUG1": return Level.DEBUG;
            case "LOG":
            case "INFO":
            case "NOTICE": return Level.INFO;
            case "WARNING": return Level.WARN;
            case "ERROR":
            case "FATAL":
            case "PANIC": return Level.ERROR;
            default:
                return Level.TRACE;
        }
    }

    /**
     * Manages the tasks related to handling positional information for PostgreSQL log files.
     * <p>
     * This method processes and updates the positional information of log files to ensure that
     * logs are correctly read and parsed from the last known position. It handles the initialization,
     * periodic updates, and finalization of positions for different log files. The method ensures
     * that log parsing can resume correctly after restarts or interruptions by accurately maintaining
     * and updating the position information.
     * </p>
     * <p>
     * The method typically involves the following tasks:
     * </p>
     * <ul>
     *   <li>Initializing the position map for new log files.</li>
     *   <li>Updating the position as logs are read and processed.</li>
     *   <li>Saving the current position to persistent storage.</li>
     * </ul>
     * <p>
     * This method is crucial for log processing scenarios where it's important to not miss any log
     * entries and to avoid reprocessing already handled logs.
     * </p>
     *
     * @throws IOException if an I/O error occurs while managing the log file positions.
     */
    protected Thread positionFileTasks() throws IOException {
        File currentPositionFile = new File(currentLogPositionFile);
        if(currentPositionFile.exists() && currentPositionFile.length()>0) {
            position.putAll(mapper.readValue(currentPositionFile,
                    new TypeReference<ConcurrentHashMap<String, Long>>() {}));
        }
        new Timer("LogPositionSaver", true).schedule(new TimerTask() {
            @Override
            public void run() {
                saveLogFilesPosition();
            }
        }, TimeUnit.SECONDS.toMillis(saveInterval), TimeUnit.SECONDS.toMillis(saveInterval));
        Thread savePostitionShutdownHook = new Thread(this::saveLogFilesPosition);
        Runtime.getRuntime().addShutdownHook(savePostitionShutdownHook);
        return savePostitionShutdownHook;
    }

    /**
     * Saves the current read positions of PostgreSQL log files.
     * <p>
     * This method writes the current positions of log files to a specified file, ensuring accurate
     * resume points after restarts or interruptions. It ensures atomicity and consistency to prevent
     * data loss.
     * </p>
     */
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

    /**
     * Reads a JSON log file and processes each line.
     * This method reads through the specified JSON log file starting from the last saved position,
     * parses each line, and processes it according to the log's severity level. It updates the
     * read position after processing each log entry to ensure accurate resumption.
     *
     * @param jsonLog the JSON log file to read
     * @throws IOException if an error occurs while reading the log file
     */
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
